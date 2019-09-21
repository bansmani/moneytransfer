import org.h2.jdbcx.JdbcDataSource
import org.objenesis.ObjenesisStd
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.util.*


@Target(AnnotationTarget.CLASS)
annotation class Schema(val name: String)


@Target(AnnotationTarget.FIELD)
annotation class Id

@Target(AnnotationTarget.FIELD)
annotation class Indexed

@Target(AnnotationTarget.CLASS)
annotation class Entity(val name: String = "")


object ConnectionPool {

    private val pool = ArrayDeque<Connection>()

    init {
        createPool()
    }

    private fun createPool() {
        val ds = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:db1")
            user = "sa"
            password = "sa"
        }
        for (i in 1..10) {
            val conn = ds.connection
            pool.add(conn)
        }
    }

    fun getConnection(): Connection {
        val conn = pool.poll()
        pool.add(conn)
        return conn
    }
}


interface ICrudRepsitory {
    fun execute(sql: String): Boolean
    fun save(entity: Any, createTableIfMissing: Boolean = false): Boolean
    fun <T> query(sql: String, entity: Class<T>): List<T>
    fun query(sql: String): ResultSet
    fun <T> query(entity: Class<T>): List<T>
    fun createTable(entity: Class<*>): Boolean

}

inline fun <reified T> T.logger(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}

object CrudRepsitory : ICrudRepsitory {


    val typeMap = mapOf<String, String>("String" to "VARCHAR", "Instant" to "TIMESTAMP")
    override fun createTable(entity: Class<*>): Boolean {
        val tableName = getTableName(entity)
        var createTablePrefix = "CREATE TABLE IF NOT EXISTS $tableName ("
        var columnString = ""
        var createIndexPrefix =
            "CREATE INDEX IF NOT EXISTS IDX_${tableName.replace(".", "_").toUpperCase()} ON $tableName("
        var indexedColumns = ""

        val fields = entity.declaredFields
        fields.forEach { field ->
            columnString += ", ${field.name} "

            columnString += typeMap[field.type.simpleName]
                ?: if (field.type.isEnum) "VARCHAR" else field.type.simpleName.toUpperCase()

            columnString += if (field.isAnnotationPresent(Id::class.java)) " PRIMARY KEY" else ""
            indexedColumns += if (field.isAnnotationPresent(Indexed::class.java)) field.name else ""

        }
        var sql = createTablePrefix + columnString.removePrefix(",").trim() + "); \n"
        if (indexedColumns != "") {
            sql += "$createIndexPrefix$indexedColumns); \n"
        }
        println(sql)
        try {
            execute(sql)
        } catch (e: Exception) {
            val schemaName = tableName.split(".").first()
            println(e.message)
            if (e.message!!.toLowerCase().contains("schema \"$schemaName\" not found")) {
                execute("create schema if not exists $schemaName")
                execute(sql)
            }
        }
        return true
    }

    override fun execute(sql: String): Boolean {
        val connection = ConnectionPool.getConnection()
        return connection.prepareStatement(sql).execute()
    }

    /***
     * createTableIfMissing is missing should be avoided in production ready
     */
    override fun save(entity: Any, createTableIfMissing: Boolean): Boolean {
        logger().warn("createTableIfMissing feature should be turned off for performance & safety")
        logger().info(entity.toString())
        val tableName = getTableName(entity)
        val columns = entity.javaClass.declaredFields.map { field -> field.name }
            .reduce { acc, field ->
                "$acc, $field"
            }.toString()

        val values = entity.javaClass.declaredFields.map { field -> field.isAccessible = true; field.get(entity) }
            .map { any ->
                if (any == null) null else "'${any.toString()}'"
            }
            .reduce { acc, value ->
                "$acc, $value"
            }.toString()


        val sql = "INSERT INTO $tableName ($columns) VALUES ($values)"
        println(sql)
        return try {
            ConnectionPool.getConnection().prepareStatement(sql).execute()
        } catch (e: Exception) {
            if (createTableIfMissing) {
                createTable(entity.javaClass)
                ConnectionPool.getConnection().prepareStatement(sql).execute()
            } else false
        }
    }

    private fun getTableName(entity: Any): String {
        return getTableName(entity.javaClass)
    }

    private fun getTableName(entity: Class<*>): String {
        val tableName = if (entity.isAnnotationPresent(Entity::class.java))
            entity.getAnnotation(Entity::class.java).name + "."
        else
            entity.simpleName.split("$").last()

        val schemaName = if (entity.isAnnotationPresent(Schema::class.java))
            entity.getAnnotation(Schema::class.java).name + "."
        else
            ""
        return schemaName + tableName
    }

    override fun query(sql: String) = ConnectionPool.getConnection().prepareStatement(sql).executeQuery()

    override fun <T> query(entity: Class<T>): List<T> {
        return query("select * from ${getTableName(entity)}", entity)
    }

    override fun <T> query(sql: String, entity: Class<T>): List<T> {
        val rs = ConnectionPool.getConnection().prepareStatement(sql).executeQuery()
        val colCaseInSensitiveMap = entity.declaredFields.map { it.name.toUpperCase() to it.name }.toMap()
        val list = arrayListOf<T>()
        while (rs.next()) {

            val instance = try {
                entity.newInstance()
            } catch (e: Exception) {
                ObjenesisStd().newInstance(entity)
            }
            for (i in 1..rs.metaData.columnCount) {
                val colname = rs.metaData.getColumnName(i)
                val field = entity.getDeclaredField(colCaseInSensitiveMap.get(colname))
                field.isAccessible = true
                when (field.type.simpleName) {
                    "long" -> field.setLong(instance, rs.getLong(i))
                    "double" -> field.setDouble(instance, rs.getDouble(i))
                    "int" -> field.setInt(instance, rs.getInt(i))
                    "String" -> field.set(instance, rs.getString(i))
                    else -> {
                        val enumclass = Class.forName(field.type.name)
                        val valueOf = enumclass.getDeclaredMethod("valueOf", String::class.java)
                        val enumvalue = valueOf.invoke(enumclass, rs.getString(i))
                        field.set(instance, enumvalue)
                    }

                }
            }
            list.add(instance as T)
        }
        @Suppress("UNCHECKED_CAST") val array = list.toArray() as Array<T>
        return listOf<T>(*array)
    }
}


