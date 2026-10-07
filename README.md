# DbUtilities

[![Maven Central](https://img.shields.io/maven-central/v/de.soderer/dbutilities)](https://central.sonatype.com/artifact/de.soderer/dbutilities)
[![Javadoc](https://javadoc.io/badge2/de.soderer/dbutilities/javadoc.svg)](https://javadoc.io/doc/de.soderer/dbutilities)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)
[![GitHub release](https://img.shields.io/github/v/release/hudeany/DbUtilities)](https://github.com/hudeany/DbUtilities/releases)

**DbUtilities** is a lightweight Java library for working with relational databases via plain JDBC.
It bundles connection handling, structure inspection, DDL parsing, schema diff/merge generation and SQL formatting
for many database vendors. It has **no external runtime dependencies**. You only add the JDBC driver of your database.

---

## Features

- **Connections**: build JDBC urls and open connections for all supported vendors, including TLS connections,
  Oracle `tnsnames.ora` lookup and file databases (SQLite, Derby, HSQL)
- **Structure inspection**: tables, columns and data types, default values, primary keys, foreign keys,
  constraints, indices and schemas
- **DDL parser**: parses `CREATE SCHEMA`, `CREATE TABLE`, `ALTER TABLE` and `COMMENT ON` scripts into a
  vendor independent `DbStructure` model
- **Migration generator**: compares two DDL scripts and writes the migration script (`ALTER TABLE …`) between them
- **Merge generator**: merges two DDL scripts into one unified DDL script
- **Data helpers**: detect, drop and join duplicates, copy table structures, insert or update rows across tables
- **SQL formatting**: pretty print SQL statements, build `SELECT` statements via an object model
- **Where clause parser**: parses simple SQL where clauses and outputs them in Oracle, MySQL or BeanShell syntax
- **Vendor aware escaping**: reserved word lists and identifier quoting per vendor

## Supported databases

| Vendor | `DbVendor` | Default port |
|---|---|---|
| Oracle | `Oracle` | 1521 |
| MySQL | `MySQL` | 3306 |
| MariaDB | `MariaDB` | 3306 |
| PostgreSQL | `PostgreSQL` | 5432 |
| Microsoft SQL Server | `MsSQL` | 1433 |
| Firebird | `Firebird` | 3050 |
| SQLite | `SQLite` | file |
| Apache Derby | `Derby` | file / embedded |
| HyperSQL (HSQLDB) | `HSQL` | file / memory / server |
| Apache Cassandra | `Cassandra` | 9042 |

Not every method supports every vendor. The Javadoc of each method lists its supported vendors.

## Installation

The library is available on [Maven Central](https://central.sonatype.com/artifact/de.soderer/dbutilities).
Replace `VERSION` with the version shown in the Maven Central badge above.

### Maven

```xml
<dependency>
	<groupId>de.soderer</groupId>
	<artifactId>dbutilities</artifactId>
	<version>VERSION</version>
</dependency>
```

### Gradle

```groovy
implementation "de.soderer:dbutilities:VERSION"
```

### Without a build tool

Download the jar from the [GitHub releases](https://github.com/hudeany/DbUtilities/releases) and add it to your classpath.

> **Note:** You also need the JDBC driver of your database on the classpath.
> `DbUtilities.getDownloadUrl(DbVendor)` returns the driver's download location.

## Usage

### Open a connection

```java
DbConnectionDefinition definition = new DbConnectionDefinition(
		DbVendor.PostgreSQL, "localhost:5432", "mydb", "user", "secret".toCharArray());

try (Connection connection = DbUtilities.createConnection(definition, false)) {
	DbVendor vendor = DbUtilities.getDbVendor(connection);
	System.out.println("Connected to " + vendor);
}
```

File databases use the file path as database name (`~` is replaced by the user's home directory):

```java
try (Connection connection = DbUtilities.createNewDatabase(DbVendor.SQLite, "~/data/test.sqlite")) {
	// ...
}
```

### Inspect the database structure

```java
// Tables matching patterns ("*" and "?" wildcards, "!" excludes)
List<String> tables = DbUtilities.getAvailableTables(connection, "customer*, !tmp_*");

// Columns with their types
CaseInsensitiveMap<DbColumnType> columns = DbUtilities.getColumnDataTypes(connection, "customer");
for (Map.Entry<String, DbColumnType> column : columns.entrySet()) {
	System.out.println(column.getKey() + ": " + column.getValue());
}

// Keys, constraints and indices
CaseInsensitiveSet primaryKey = DbUtilities.getPrimaryKeyColumns(connection, "customer");
List<DatabaseForeignKey> foreignKeys = DbUtilities.getForeignKeys(connection, "orders");
List<DatabaseIndex> indices = DbUtilities.getIndices(connection, "orders");
```

### Parse DDL scripts

```java
try (InputStream ddl = new FileInputStream("schema.sql")) {
	DbStructure structure = SqlDdlParser.parse(ddl);
	for (DbSchema schema : structure.getSchemas().values()) {
		for (DbTable table : schema.getTables().values()) {
			System.out.println(table.getTableName() + " " + table.getColumns().keySet());
		}
	}
}
```

Tables without a schema name are stored in the schema with the empty name `""`.

### Generate a migration script

```java
try (InputStream current = new FileInputStream("schema_v1.sql");
		InputStream desired = new FileInputStream("schema_v2.sql");
		OutputStream migration = new FileOutputStream("migrate_v1_to_v2.sql")) {
	SqlDdlMigrationGenerator.diff(current, desired, migration);
}
```

The generated script starts with a statistics block (created and dropped tables, changed columns, keys, comments)
followed by the statements. It is written in PostgreSQL dialect.

### Merge two DDL scripts

```java
try (InputStream a = new FileInputStream("module_a.sql");
		InputStream b = new FileInputStream("module_b.sql");
		OutputStream merged = new FileOutputStream("merged.sql")) {
	// Arguments: sortBySchema, sortByTable, sortByColumn. Structure B wins on conflicts.
	SqlDdlMergeGenerator.merge(a, b, merged, true, true, false);
}
```

### Handle duplicates

```java
// Keep only the first row of each email address (case-insensitive)
int deleted = DbUtilities.dropDuplicates(connection, "customer", Arrays.asList("LOWER(email)"));
```

### Format SQL

```java
String formatted = new SqlStatementFormatter(DbVendor.Oracle, "\t", "\n")
		.format("select a.id, b.name from orders a join customer b on a.customer_id = b.id where a.total > 100");
```

### Parse simple where clauses

```java
Map<String, Value.Type> fields = new HashMap<>();
fields.put("age", Value.Type.Number);
fields.put("name", Value.Type.String);

RulePart rule = ReducedSqlWhereClauseParser.parse("age >= 18 and lower(name) like 'a%'", fields);
System.out.println(rule.toString(RulePart.StringType.MySQL));
```

### Read Oracle tnsnames.ora

```java
try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new FileInputStream("tnsnames.ora"))) {
	Map<String, OracleTnsMapValue> entries = reader.read();
	String jdbcDescription = OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(entries.get("XE"));
}
```

## Security notes

- Table and column names are concatenated into SQL statements. **Never pass untrusted user input** as names or table patterns.
- Secure connections without a truststore trust the server certificate without validation
  (Oracle: trust on first use, MySQL/MariaDB/MsSQL: `trustServerCertificate=true`).
  Configure a truststore in `DbConnectionDefinition` for protection against man-in-the-middle attacks.

## Building

The project is built with Apache Ant and has no external compile dependencies.
The Javadoc builds without warnings using `-Xdoclint:all`.

## Links

- [Maven Central](https://central.sonatype.com/artifact/de.soderer/dbutilities)
- [Javadoc](https://javadoc.io/doc/de.soderer/dbutilities)
- [GitHub releases](https://github.com/hudeany/DbUtilities/releases)
