package de.soderer.utilities.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import de.soderer.utilities.db.DatabaseConstraint.ConstraintType;
import de.soderer.utilities.db.data.DbColumnType;
import de.soderer.utilities.db.data.DbConnectionDefinition;
import de.soderer.utilities.db.data.DbSimpleDataType;
import de.soderer.utilities.db.data.DbVendor;
import de.soderer.utilities.db.exception.DbNotExistsException;
import de.soderer.utilities.db.oracle.OracleTnsMapValue;
import de.soderer.utilities.db.oracle.OracleTnsnamesReader;
import de.soderer.utilities.db.utilities.CaseInsensitiveMap;
import de.soderer.utilities.db.utilities.CaseInsensitiveSet;
import de.soderer.utilities.db.utilities.Utilities;

/**
 * Static helper methods for JDBC database connections, database structure information (tables, columns,
 * keys, indices, constraints), simple DDL operations and duplicate handling for several database vendors
 * (see {@link DbVendor}).
 * <p>
 * Watch out: Table and column names given to these methods are mostly concatenated into SQL statements,
 * so they must never contain untrusted user input.
 */
public class DbUtilities {
	/**
	 * Creates a new instance. All methods are static, so this is only needed for compatibility.
	 */
	public DbUtilities() {
		// Only static methods
	}

	/**
	 * Download location of the MySQL JDBC driver.
	 */
	public static final String DOWNLOAD_LOCATION_MYSQL = "https://dev.mysql.com/downloads/connector/j";
	/**
	 * Download location of the MariaDB JDBC driver.
	 */
	public static final String DOWNLOAD_LOCATION_MARIADB = "https://downloads.mariadb.org/connector-java";
	/**
	 * Download location of the Oracle JDBC driver.
	 */
	public static final String DOWNLOAD_LOCATION_ORACLE = "http://www.oracle.com/technetwork/apps-tech/jdbc-112010-090769.html";
	/**
	 * Download location of the PostgreSQL JDBC driver.
	 */
	public static final String DOWNLOAD_LOCATION_POSTGRESQL = "https://jdbc.postgresql.org/download.html";
	/**
	 * Download location of the Firebird JDBC driver.
	 */
	public static final String DOWNLOAD_LOCATION_FIREBIRD = "http://www.firebirdsql.org/en/jdbc-driver";
	/**
	 * Download location of the Apache Derby JDBC driver.
	 */
	public static final String DOWNLOAD_LOCATION_DERBY = "https://db.apache.org/derby/derby_downloads.html";
	/**
	 * Download location of the SQLite JDBC driver.
	 */
	public static final String DOWNLOAD_LOCATION_SQLITE = "https://bitbucket.org/xerial/sqlite-jdbc/downloads";
	/**
	 * Download location of the HSQL JDBC driver.
	 */
	public static final String DOWNLOAD_LOCATION_HSQL = "http://hsqldb.org/download";
	/**
	 * Download location of the Microsoft SQL Server JDBC driver.
	 */
	public static final String DOWNLOAD_LOCATION_MSSQL = "https://msdn.microsoft.com/de-de/library/mt683464(v=sql.110).aspx";

	/**
	 * SQL operators, used to split SQL statements into tokens.
	 */
	public static final List<String> SQL_OPERATORS = Arrays.asList(new String[] { "+", "-", "*", "/", "%", "&", "|",
			"^", "=", "!=", ">", "<", ">=", "<=", "<>", "+=", "-=", "*=", "/=", "%=", "&=", "||", "^-=", "|*=" });

	/**
	 * Pattern of identifiers that never need to be quoted for syntax reasons (reserved words may still need quoting).
	 */
	public static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

	/**
	 * Reserved words of PostgreSQL, which must be quoted when used as identifiers.
	 */
	public static final CaseInsensitiveSet RESERVED_WORDS_POSTGRESQL = new CaseInsensitiveSet(new String[] { "abs", "absent", "acos", "all", "allocate", "alter", "analyse", "analyze",
			"and", "any", "any_value", "are", "array", "array_agg", "array_max_cardinality", "as", "asc", "asensitive", "asin", "asymmetric", "at", "atan", "atomic", "authorization",
			"avg", "begin", "begin_frame", "begin_partition", "between", "bigint", "binary", "bit", "blob", "boolean", "both", "btrim", "by", "call", "called", "cardinality", "cascaded",
			"case", "cast", "ceil", "ceiling", "char", "char_length", "character", "character_length", "check", "classifier", "clob", "close", "coalesce", "collate", "collation", "collect",
			"column", "commit", "concurrently", "condition", "connect", "constraint", "contains", "convert", "copy", "corr", "corresponding", "cos", "cosh", "count", "covar_pop", "covar_samp",
			"create", "cross", "cube", "cume_dist", "current", "current_catalog", "current_date", "current_path", "current_role", "current_row", "current_schema", "current_time",
			"current_timestamp", "current_user", "current_default_transform_group", "current_transform_group_for_type", "cursor", "cycle", "datalink", "date", "day", "deallocate",
			"dec", "decfloat", "decimal", "declare", "default", "deferrable", "define", "delete", "dense_rank", "deref", "desc", "describe", "deterministic", "disconnect", "distinct",
			"dlnewcopy", "dlpreviouscopy", "dlurlcomplete", "dlurlcompleteonly", "dlurlcompletewrite", "dlurlpath", "dlurlpathonly", "dlurlpathwrite", "dlurlscheme", "dlurlserver", "dlvalue",
			"do", "double", "drop", "dynamic", "each", "element", "else", "empty", "end", "end-exec", "end_frame", "end_partition", "equals", "escape", "every", "except", "exec", "execute",
			"exists", "exp", "external", "extract", "false", "fetch", "filter", "first_value", "float", "floor", "for", "foreign", "frame_row", "free", "freeze", "from", "full", "function",
			"fusion", "get", "global", "grant", "greatest", "group", "grouping", "groups", "having", "hold", "hour", "identity", "ilike", "import", "in", "indicator", "initial", "initially",
			"inner", "inout", "insensitive", "insert", "int", "integer", "intersect", "intersection", "interval", "into", "is", "isnull", "join", "json", "json_array", "json_arrayagg",
			"json_exists", "json_object", "json_objectagg", "json_query", "json_scalar", "json_serialize", "json_table", "json_table_primitive", "json_value", "lag", "language", "large",
			"last_value", "lateral", "lead", "leading", "least", "left", "like", "like_regex", "limit", "listagg", "ln", "local", "localtime", "localtimestamp", "log", "log10", "lower",
			"lpad", "ltrim", "match", "match_number", "match_recognize", "matches", "max", "member", "merge", "merge_action", "method", "min", "minute", "mod", "modifies", "module", "month",
			"multiset", "national", "natural", "nchar", "nclob", "new", "no", "none", "normalize", "not", "notnull", "nth_value", "ntile", "null", "nullif", "numeric", "occurrences_regex",
			"octet_length", "of", "offset", "old", "omit", "on", "one", "only", "open", "or", "order", "out", "outer", "over", "overlaps", "overlay", "parameter", "partition", "pattern",
			"per", "percent", "percent_rank", "percentile_cont", "percentile_disc", "period", "placing", "portion", "position", "position_regex", "power", "precedes", "precision", "prepare",
			"primary", "procedure", "ptf", "range", "rank", "reads", "real", "recursive", "ref", "references", "referencing", "regr_avgx", "regr_avgy", "regr_count", "regr_intercept",
			"regr_r2", "regr_slope", "regr_sxx", "regr_sxy", "regr_syy", "release", "result", "return", "returning", "returns", "revoke", "right", "rollback", "rollup", "row", "row_number",
			"rows", "rpad", "rtrim", "running", "savepoint", "scope", "scroll", "search", "second", "seek", "select", "sensitive", "session_user", "set", "setof", "show", "similar", "sin",
			"sinh", "skip", "smallint", "some", "specific", "specifictype", "sql", "sqlexception", "sqlstate", "sqlwarning", "sqrt", "start", "static", "stddev_pop", "stddev_samp",
			"submultiset", "subset", "substring", "substring_regex", "succeeds", "sum", "symmetric", "system", "system_time", "system_user", "table", "tablesample", "tan", "tanh", "then",
			"time", "timestamp", "timezone_hour", "timezone_minute", "to", "trailing", "translate", "translate_regex", "translation", "treat", "trigger", "trim", "trim_array", "true",
			"truncate", "uescape", "union", "unique", "unknown", "unnest", "update", "upper", "user", "using", "value", "value_of", "values", "var_pop", "var_samp", "varbinary", "varchar",
			"variadic", "varying", "verbose", "versioning", "when", "whenever", "where", "width_bucket", "window", "with", "within", "without", "xml", "xmlagg", "xmlattributes", "xmlbinary",
			"xmlcast", "xmlcomment", "xmlconcat", "xmldocument", "xmlelement", "xmlexists", "xmlforest", "xmliterate", "xmlnamespaces", "xmlparse", "xmlpi", "xmlquery", "xmlroot",
			"xmlserialize", "xmltable", "xmltext", "xmlvalidate", "year" });

	/**
	 * Reserved words of Oracle, which must be quoted when used as identifiers.
	 */
	public static final CaseInsensitiveSet RESERVED_WORDS_ORACLE = new CaseInsensitiveSet(new String[] { "access", "account", "activate", "add", "admin", "advise", "after", "all",
			"all_rows", "allocate", "alter", "analyze", "and", "any", "archive", "archivelog", "array", "as", "asc", "at", "audit", "authenticated", "authorization", "autoextend",
			"automatic", "backup", "become", "before", "begin", "between", "bfile", "bitmap", "blob", "block", "body", "by", "cache", "cache_instances", "cancel", "cascade",
			"cast", "cfile", "chained", "change", "char", "char_cs", "character", "check", "checkpoint", "choose", "chunk", "clear", "clob", "clone", "close",
			"close_cached_open_cursors", "cluster", "coalesce", "column", "column_value", "columns", "comment", "commit", "committed", "compatibility", "compile", "complete",
			"composite_limit", "compress", "compute", "connect", "connect_time", "constraint", "constraints", "contents", "continue", "controlfile", "convert", "cost",
			"cpu_per_call", "cpu_per_session", "create", "current", "current_user", "current_schema", "cursor", "cycle", "dangling", "database", "datafile", "datafiles",
			"dataobjno", "date", "dba", "dbhigh", "dblow", "dbmac", "deallocate", "debug", "dec", "decimal", "declare", "default", "deferrable", "deferred", "degree", "delete",
			"deref", "desc", "directory", "disable", "disconnect", "dismount", "distinct", "distributed", "dml", "double", "drop", "dump", "each", "else", "enable", "end",
			"enforce", "entry", "escape", "except", "exceptions", "exchange", "excluding", "exclusive", "execute", "exists", "expire", "explain", "extent", "extents", "externally",
			"failed_login_attempts", "false", "fast", "file", "first_rows", "flagger", "float", "flob", "flush", "for", "force", "foreign", "freelist", "freelists", "from", "full",
			"function", "global", "global_name", "globally", "grant", "group", "groups", "hash", "hashkeys", "having", "header", "heap", "identified", "idgenerators", "idle_time",
			"if", "immediate", "in", "including", "increment", "ind_partition", "index", "indexed", "indexes", "indicator", "initial", "initially", "initrans", "insert",
			"instance", "instances", "instead", "int", "integer", "intermediate", "intersect", "into", "is", "isolation", "isolation_level", "keep", "key", "kill", "label",
			"layer", "less", "level", "library", "like", "limit", "link", "list", "lob", "local", "lock", "locked", "log", "logfile", "logging", "logical_reads_per_call",
			"logical_reads_per_session", "long", "manage", "master", "max", "maxarchlogs", "maxdatafiles", "maxextents", "maxinstances", "maxlogfiles", "maxloghistory",
			"maxlogmembers", "maxsize", "maxtrans", "maxvalue", "member", "min", "minextents", "minimum", "minus", "minvalue", "mls_label_format", "mlslabel", "mode", "modify",
			"mount", "move", "mts_dispatchers", "multiset", "national", "nchar", "nchar_cs", "nclob", "needed", "nested", "nested_table_id", "network", "new", "next",
			"noarchivelog", "noaudit", "nocache", "nocompress", "nocycle", "noforce", "nologging", "nomaxvalue", "nominvalue", "none", "noorder", "nooverride", "noparallel",
			"noreverse", "normal", "nosort", "not", "nothing", "nowait", "null", "number", "numeric", "nvarchar2", "object", "objno", "objno_reuse", "of", "off", "offline", "oid",
			"oidindex", "old", "on", "online", "only", "opcode", "open", "optimal", "optimizer_goal", "option", "or", "order", "organization", "oslabel", "overflow", "own",
			"package", "parallel", "partition", "password", "password_grace_time", "password_life_time", "password_lock_time", "password_reuse_max", "password_reuse_time",
			"password_verify_function", "pctfree", "pctincrease", "pctthreshold", "pctused", "pctversion", "percent", "permanent", "plan", "plsql_debug", "post_transaction",
			"precision", "preserve", "primary", "prior", "private", "private_sga", "privilege", "privileges", "procedure", "profile", "public", "purge", "queue", "quota", "range",
			"raw", "rba", "read", "readup", "real", "rebuild", "recover", "recoverable", "recovery", "ref", "references", "referencing", "refresh", "rename", "replace", "reset",
			"resetlogs", "resize", "resource", "restricted", "return", "returning", "reuse", "reverse", "revoke", "role", "roles", "rollback", "row", "rowid", "rownum", "rows",
			"rule", "sample", "savepoint", "sb4", "scan_instances", "schema", "scn", "scope", "sd_all", "sd_inhibit", "sd_show", "seg_block", "seg_file", "segment", "select",
			"sequence", "serializable", "session", "session_cached_cursors", "sessions_per_user", "set", "share", "shared", "shared_pool", "shrink", "size", "skip",
			"skip_unusable_indexes", "smallint", "snapshot", "some", "sort", "specification", "split", "sql_trace", "standby", "start", "statement_id", "statistics", "stop",
			"storage", "store", "structure", "successful", "switch", "synonym", "sys_op_enforce_not_null$", "sys_op_ntcimg$", "sysdate", "sysdba", "sysoper", "system", "table",
			"tables", "tablespace", "tablespace_no", "tabno", "temporary", "than", "the", "then", "thread", "time", "timestamp", "to", "toplevel", "trace", "tracing",
			"transaction", "transitional", "trigger", "triggers", "true", "truncate", "tx", "type", "ub2", "uba", "uid", "unarchived", "undo", "union", "unique", "unlimited",
			"unlock", "unrecoverable", "until", "unusable", "unused", "updatable", "update", "usage", "use", "user", "using", "validate", "validation", "value", "values",
			"varchar", "varchar2", "varying", "view", "when", "whenever", "where", "with", "without", "work", "write", "writedown", "writeup", "xid", "year", "zone" });

	/**
	 * Reserved words of MySQL and MariaDB, which must be quoted when used as identifiers.
	 */
	public static final CaseInsensitiveSet RESERVED_WORDS_MYSSQL_MARIADB = new CaseInsensitiveSet(new String[] { "accessible", "account", "action", "active", "add", "admin",
			"after", "against", "aggregate", "algorithm", "all", "alter", "always", "analyse", "analyze", "and", "any", "array", "as", "asc", "ascii", "asensitive", "at",
			"attribute", "authentication", "auto_increment", "autoextend_size", "avg", "avg_row_length", "backup", "before", "begin", "between", "bigint", "binary", "binlog",
			"bit", "blob", "block", "bool", "boolean", "both", "btree", "buckets", "by", "byte", "cache", "call", "cascade", "cascaded", "case", "catalog_name", "chain",
			"challenge_response", "change", "changed", "channel", "char", "character", "charset", "check", "checksum", "cipher", "class_origin", "client", "clone", "close",
			"coalesce", "code", "collate", "collation", "column", "column_format", "column_name", "columns", "comment", "commit", "committed", "compact", "completion", "component",
			"compressed", "compression", "concurrent", "condition", "connection", "consistent", "constraint", "constraint_catalog", "constraint_name", "constraint_schema",
			"contains", "context", "continue", "convert", "cpu", "create", "cross", "cube", "cume_dist", "current", "current_date", "current_role", "current_time",
			"current_timestamp", "current_user", "cursor", "cursor_name", "data", "database", "databases", "datafile", "date", "datetime", "day", "day_hour", "day_microsecond",
			"day_minute", "day_second", "deallocate", "dec", "decimal", "declare", "default", "default_auth", "definer", "definition", "delay_key_write", "delayed", "delete",
			"delete_domain_id", "dense_rank", "des_key_file", "desc", "describe", "description", "deterministic", "diagnostics", "directory", "disable", "discard", "disk",
			"distinct", "distinctrow", "div", "do", "do_domain_ids", "double", "drop", "dual", "dumpfile", "duplicate", "dynamic", "each", "else", "elseif", "empty", "enable",
			"enclosed", "encryption", "end", "ends", "enforced", "engine", "engine_attribute", "engines", "enum", "error", "errors", "escape", "escaped", "event", "events",
			"every", "except", "exchange", "exclude", "execute", "exists", "exit", "expansion", "expire", "explain", "export", "extended", "extent_size", "factor",
			"failed_login_attempts", "false", "fast", "faults", "fetch", "fields", "file", "file_block_size", "filter", "finish", "first", "first_value", "fixed", "float",
			"float4", "float8", "flush", "following", "follows", "for", "force", "foreign", "format", "found", "from", "full", "fulltext", "function", "general", "generated",
			"geomcollection", "geometry", "geometrycollection", "get", "get_format", "get_master_public_key", "get_source_public_key", "global", "grant", "grants", "group",
			"group_replication", "grouping", "groups", "gtid_only", "handler", "hash", "having", "help", "high_priority", "histogram", "history", "host", "hosts", "hour",
			"hour_microsecond", "hour_minute", "hour_second", "identified", "if", "ignore", "ignore_domain_ids", "ignore_server_ids", "import", "in", "inactive", "index",
			"indexes", "infile", "initial", "initial_size", "initiate", "inner", "inout", "insensitive", "insert", "insert_method", "install", "instance", "int", "int1", "int2",
			"int3", "int4", "int8", "integer", "intersect", "interval", "into", "invisible", "invoker", "io", "io_after_gtids", "io_before_gtids", "io_thread", "ipc", "is",
			"isolation", "issuer", "iterate", "join", "json", "json_table", "json_value", "key", "key_block_size", "keyring", "keys", "kill", "lag", "language", "last",
			"last_value", "lateral", "lead", "leading", "leave", "leaves", "left", "less", "level", "like", "limit", "linear", "lines", "linestring", "list", "load", "local",
			"localtime", "localtimestamp", "lock", "locked", "locks", "logfile", "logs", "long", "longblob", "longtext", "loop", "low_priority", "master", "master_auto_position",
			"master_bind", "master_compression_algorithms", "master_connect_retry", "master_delay", "master_heartbeat_period", "master_host", "master_log_file", "master_log_pos",
			"master_password", "master_port", "master_public_key_path", "master_retry_count", "master_server_id", "master_ssl", "master_ssl_ca", "master_ssl_capath",
			"master_ssl_cert", "master_ssl_cipher", "master_ssl_crl", "master_ssl_crlpath", "master_ssl_key", "master_ssl_verify_server_cert", "master_tls_ciphersuites",
			"master_tls_version", "master_user", "master_zstd_compression_level", "match", "max_connections_per_hour", "max_queries_per_hour", "max_rows", "max_size",
			"max_updates_per_hour", "max_user_connections", "maxvalue", "medium", "mediumblob", "mediumint", "mediumtext", "member", "memory", "merge", "message_text",
			"microsecond", "middleint", "migrate", "min_rows", "minute", "minute_microsecond", "minute_second", "mod", "mode", "modifies", "modify", "month", "multilinestring",
			"multipoint", "multipolygon", "mutex", "mysql_errno", "name", "names", "national", "natural", "nchar", "ndb", "ndbcluster", "nested", "network_namespace", "never",
			"new", "next", "no", "no_wait", "no_write_to_binlog", "nodegroup", "none", "not", "nowait", "nth_value", "ntile", "null", "nulls", "number", "numeric", "nvarchar",
			"of", "off", "offset", "oj", "old", "on", "one", "only", "open", "optimize", "optimizer_costs", "option", "optional", "optionally", "options", "or", "order",
			"ordinality", "organization", "others", "out", "outer", "outfile", "over", "owner", "pack_keys", "page", "page_checksum", "parse_vcol_expr", "parser", "partial",
			"partition", "partitioning", "partitions", "password", "password_lock_time", "path", "percent_rank", "persist", "persist_only", "phase", "plugin", "plugin_dir",
			"plugins", "point", "polygon", "port", "position", "precedes", "preceding", "precision", "prepare", "preserve", "prev", "primary", "privilege_checks_user",
			"privileges", "procedure", "process", "processlist", "profile", "profiles", "proxy", "purge", "quarter", "query", "quick", "random", "range", "rank", "read",
			"read_only", "read_write", "reads", "real", "rebuild", "recover", "recursive", "redo_buffer_size", "redofile", "redundant", "ref_system_id", "reference", "references",
			"regexp", "registration", "relay", "relay_log_file", "relay_log_pos", "relay_thread", "relaylog", "release", "reload", "remote", "remove", "rename", "reorganize",
			"repair", "repeat", "repeatable", "replace", "replica", "replicas", "replicate_do_db", "replicate_do_table", "replicate_ignore_db", "replicate_ignore_table",
			"replicate_rewrite_db", "replicate_wild_do_table", "replicate_wild_ignore_table", "replication", "require", "require_row_format", "reset", "resignal", "resource",
			"respect", "restart", "restore", "restrict", "resume", "retain", "return", "returned_sqlstate", "returning", "returns", "reuse", "reverse", "revoke", "right", "rlike",
			"role", "rollback", "rollup", "rotate", "routine", "row", "row_count", "row_format", "row_number", "rows", "rtree", "savepoint", "schedule", "schema", "schema_name",
			"schemas", "second", "second_microsecond", "secondary", "secondary_engine", "secondary_engine_attribute", "secondary_load", "secondary_unload", "security", "select",
			"sensitive", "separator", "serial", "serializable", "server", "session", "set", "share", "show", "shutdown", "signal", "signed", "simple", "skip", "slave", "slow",
			"smallint", "snapshot", "socket", "some", "soname", "sounds", "source", "source_auto_position", "source_bind", "source_compression_algorithms", "source_connect_retry",
			"source_delay", "source_heartbeat_period", "source_host", "source_log_file", "source_log_pos", "source_password", "source_port", "source_public_key_path",
			"source_retry_count", "source_ssl", "source_ssl_ca", "source_ssl_capath", "source_ssl_cert", "source_ssl_cipher", "source_ssl_crl", "source_ssl_crlpath",
			"source_ssl_key", "source_ssl_verify_server_cert", "source_tls_ciphersuites", "source_tls_version", "source_user", "source_zstd_compression_level", "spatial",
			"specific", "sql", "sql_after_gtids", "sql_after_mts_gaps", "sql_before_gtids", "sql_big_result", "sql_buffer_result", "sql_cache", "sql_calc_found_rows",
			"sql_no_cache", "sql_small_result", "sql_thread", "sql_tsi_day", "sql_tsi_hour", "sql_tsi_minute", "sql_tsi_month", "sql_tsi_quarter", "sql_tsi_second", "sql_tsi_week",
			"sql_tsi_year", "sqlexception", "sqlstate", "sqlwarning", "srid", "ssl", "stacked", "start", "starting", "starts", "stats_auto_recalc", "stats_persistent",
			"stats_sample_pages", "status", "stop", "storage", "stored", "straight_join", "stream", "string", "subclass_origin", "subject", "subpartition", "subpartitions",
			"super", "suspend", "swaps", "switches", "system", "table", "table_checksum", "table_name", "tables", "tablespace", "temporary", "temptable", "terminated", "text",
			"than", "then", "thread_priority", "ties", "time", "timestamp", "timestampadd", "timestampdiff", "tinyblob", "tinyint", "tinytext", "tls", "to", "trailing",
			"transaction", "trigger", "triggers", "true", "truncate", "type", "types", "unbounded", "uncommitted", "undefined", "undo", "undo_buffer_size", "undofile", "unicode",
			"uninstall", "union", "unique", "unknown", "unlock", "unregister", "unsigned", "until", "update", "upgrade", "usage", "use", "use_frm", "user", "user_resources",
			"using", "utc_date", "utc_time", "utc_timestamp", "validation", "value", "values", "varbinary", "varchar", "varcharacter", "variables", "varying", "vcpu", "view",
			"virtual", "visible", "wait", "warnings", "week", "weight_string", "when", "where", "while", "window", "with", "without", "work", "wrapper", "write", "x509", "xa",
			"xid", "xml", "xor", "year", "year_month", "zerofill", "zone" });

	/**
	 * Reserved words of Apache Derby, which must be quoted when used as identifiers.
	 */
	public static final CaseInsensitiveSet RESERVED_WORDS_DERBY = new CaseInsensitiveSet(new String[] { "add", "all", "allocate",
			"alter", "and", "any", "are", "as", "asc", "assertion", "at", "authorization", "avg", "begin", "between",
			"bit", "boolean", "both", "by", "call", "cascade", "cascaded", "case", "cast", "char", "character", "check",
			"close", "collate", "collation", "column", "commit", "connect", "connection", "constraint", "constraints",
			"continue", "convert", "corresponding", "count", "create", "current", "current_date", "current_time",
			"current_timestamp", "current_user", "cursor", "deallocate", "dec", "decimal", "declare", "deferrable",
			"deferred", "delete", "desc", "describe", "diagnostics", "disconnect", "distinct", "double", "drop", "else",
			"end", "endexec", "escape", "except", "exception", "exec", "execute", "exists", "explain", "external",
			"false", "fetch", "first", "float", "for", "foreign", "found", "from", "full", "function", "get",
			"get_current_connection", "global", "go", "goto", "grant", "group", "having", "hour", "identity",
			"immediate", "in", "indicator", "initially", "inner", "inout", "input", "insensitive", "insert", "int",
			"integer", "intersect", "into", "is", "isolation", "join", "key", "last", "left", "like", "longint",
			"lower", "ltrim", "match", "max", "min", "minute", "national", "natural", "nchar", "nvarchar", "next", "no",
			"not", "null", "nullif", "numeric", "of", "on", "only", "open", "option", "or", "order", "out", "outer",
			"output", "overlaps", "pad", "partial", "prepare", "preserve", "primary", "prior", "privileges",
			"procedure", "public", "read", "real", "references", "relative", "restrict", "revoke", "right", "rollback",
			"rows", "rtrim", "schema", "scroll", "second", "select", "session_user", "set", "smallint", "some", "space",
			"sql", "sqlcode", "sqlerror", "sqlstate", "substr", "substring", "sum", "system_user", "table", "temporary",
			"timezone_hour", "timezone_minute", "to", "trailing", "transaction", "translate", "translation", "true",
			"union", "unique", "unknown", "update", "upper", "user", "using", "values", "varchar", "varying", "view",
			"whenever", "where", "with", "work", "write", "xml", "xmlexists", "xmlparse", "xmlserialize", "year" });

	/**
	 * Output stream discarding all data, used to prevent the creation of the file "derby.log".
	 */
	public static final OutputStream DEV_NULL = new OutputStream() {
		@Override
		public void write(final int b) {
			// Do nothing
		}
	};

	/**
	 * Type code of the special Oracle type {@code oracle.sql.TIMESTAMPTZ}, returned for example by the statement
	 * {@code SELECT CURRENT_TIMESTAMP FROM DUAL}. It is not listed in {@link java.sql.Types}, but can be read via
	 * {@link java.sql.ResultSet#getTimestamp(int)} into a normal {@link java.sql.Timestamp} object.
	 */
	public static final int ORACLE_TIMESTAMPTZ_TYPECODE = -101;

	/**
	 * Generates the JDBC url for a database connection.
	 * <p>
	 * For Oracle, an entry of a tnsnames.ora file (in $TNS_ADMIN or $ORACLE_HOME/network/admin) with the name dbName
	 * is preferred. A dbName enclosed in brackets is used as TNS description, a dbName starting with "/" is used as
	 * service name instead of a SID.
	 *
	 * @param dbVendor database vendor
	 * @param dbServerHostname hostname of the database server (not used for file databases)
	 * @param dbServerPort port of the database server, 0 or less for the vendor's default port
	 * @param dbName database name, or the path for file databases ("~" is replaced by the user's home directory)
	 * @param secureConnection true to create a url for a secure (TLS) connection (Oracle, MySQL, MariaDB and MsSQL only)
	 * @param trustStoreFile optional JKS truststore file for secure MySQL and MariaDB connections
	 * @param trustStorePassword optional password of the truststore file
	 * @param trustedCN optional expected CN of the server certificate for secure Oracle connections
	 * @return JDBC url
	 * @throws Exception if the vendor is unknown, a secure connection is not supported or a tnsnames.ora file cannot be read
	 */
	public static String generateUrlConnectionString(final DbVendor dbVendor, String dbServerHostname, int dbServerPort, String dbName, final boolean secureConnection, final File trustStoreFile, final char[] trustStorePassword, final String trustedCN) throws Exception {
		if (secureConnection && dbVendor != DbVendor.Oracle && dbVendor != DbVendor.MySQL && dbVendor != DbVendor.MariaDB && dbVendor != DbVendor.MsSQL) {
			throw new Exception("Secure connection is only supported for database vendors Oracle, MySQL, MariaDB, MsSQL");
		}

		if (DbVendor.Oracle == dbVendor) {
			if (Utilities.isNotBlank(System.getenv("TNS_ADMIN"))) {
				final File tnsNamesOraFile = new File(System.getenv("TNS_ADMIN"), "tnsnames.ora");
				if (tnsNamesOraFile.exists()) {
					Map<String, OracleTnsMapValue> tnsNamesValues;
					try (OracleTnsnamesReader oracleTnsnamesReader = new OracleTnsnamesReader(new FileInputStream(tnsNamesOraFile))) {
						tnsNamesValues = oracleTnsnamesReader.read();
					}
					if (tnsNamesValues != null && tnsNamesValues.containsKey(dbName)) {
						final String fullEntry = OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(tnsNamesValues.get(dbName));
						if (Utilities.isNotBlank(fullEntry)) {
							return "jdbc:oracle:thin:@" + fullEntry;
						}
					}
				}
			}

			if (Utilities.isNotBlank(System.getenv("ORACLE_HOME"))) {
				final File tnsNamesOraFile = new File(System.getenv("ORACLE_HOME") + File.separator + "network" + File.separator + "admin" + File.separator + "tnsnames.ora");
				if (tnsNamesOraFile.exists()) {
					Map<String, OracleTnsMapValue> tnsNamesValues;
					try (OracleTnsnamesReader oracleTnsnamesReader = new OracleTnsnamesReader(new FileInputStream(tnsNamesOraFile))) {
						tnsNamesValues = oracleTnsnamesReader.read();
					}
					if (tnsNamesValues != null && tnsNamesValues.containsKey(dbName)) {
						final String fullEntry = OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(tnsNamesValues.get(dbName));
						if (Utilities.isNotBlank(fullEntry)) {
							return "jdbc:oracle:thin:@" + fullEntry;
						}
					}
				}
			}

			if (dbName.trim().startsWith("(") && dbName.trim().endsWith(")")) {
				return "jdbc:oracle:thin:@" + dbName.trim();
			}

			if (!secureConnection) {
				if (dbName.startsWith("/")) {
					// Newer Oracle databases only accept the SERVICENAME instead of SID. To use a SERVICENAME start it with prefix "/"
					return "jdbc:oracle:thin:@" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + dbName;
				} else {
					return "jdbc:oracle:thin:@" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + ":" + dbName;
				}
			} else {
				// For trustedCN you must also enforce server certificate DN check for oracle connections, default is false
				//props.setProperty("oracle.net.ssl_server_dn_match", "true");

				return "jdbc:oracle:thin:@"
				+ "(DESCRIPTION="
				+ "(ADDRESS=(PROTOCOL=TCPS)(HOST=" + dbServerHostname + ")(PORT=" + dbServerPort + "))"
				+ "(CONNECT_DATA=(SERVICE_NAME=" + dbName + "))"
				+ (Utilities.isNotBlank(trustedCN) ? "(SECURITY=(ssl_server_cert_dn=\"" + trustedCN + "\"))" : "")
				+ ")";
			}
		} else if (DbVendor.MySQL == dbVendor) {
			final String additionalParameters = "?useEncoding=true&useUnicode=true&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull";
			if (secureConnection) {
				if (trustStoreFile != null) {
					if (trustStorePassword != null) {
						return "jdbc:mysql://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName + additionalParameters + "&useSSL=true&trustStore=" + trustStoreFile.getAbsolutePath() + "&trustStorePassword=" + new String(trustStorePassword);
					} else {
						return "jdbc:mysql://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName + additionalParameters + "&useSSL=true&trustStore=" + trustStoreFile.getAbsolutePath();
					}
				} else {
					return "jdbc:mysql://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName + additionalParameters + "&useSSL=true&trustServerCertificate=true";
				}
			} else {
				return "jdbc:mysql://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName + additionalParameters;
			}
		} else if (DbVendor.MariaDB == dbVendor) {
			final String additionalParameters = "?useEncoding=true&useUnicode=true&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull";
			if (secureConnection) {
				if (trustStoreFile != null) {
					if (trustStorePassword != null) {
						return "jdbc:mariadb://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName + additionalParameters + "&useSSL=true&trustStore=" + trustStoreFile.getAbsolutePath() + "&trustStorePassword=" + new String(trustStorePassword);
					} else {
						return "jdbc:mariadb://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName + additionalParameters + "&useSSL=true&trustStore=" + trustStoreFile.getAbsolutePath();
					}
				} else {
					return "jdbc:mariadb://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName + additionalParameters + "&useSSL=true&trustServerCertificate=true";
				}
			} else {
				return "jdbc:mariadb://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName + additionalParameters;
			}
		} else if (DbVendor.PostgreSQL == dbVendor) {
			return "jdbc:postgresql://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName;
		} else if (DbVendor.SQLite == dbVendor) {
			return "jdbc:sqlite:" + Utilities.replaceUsersHome(dbName);
		} else if (DbVendor.Derby == dbVendor) {
			return "jdbc:derby:" + Utilities.replaceUsersHome(dbName);
		} else if (DbVendor.Firebird == dbVendor) {
			return "jdbc:firebirdsql:" + dbServerHostname + "/" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + ":" + Utilities.replaceUsersHome(dbName);
		} else if (DbVendor.HSQL == dbVendor) {
			dbName = Utilities.replaceUsersHome(dbName);
			if (dbName.startsWith("/") || dbName.matches(".\\:\\\\.*")) {
				return "jdbc:hsqldb:file:" + dbName + ";shutdown=true";
			} else if (Utilities.isNotBlank(dbServerHostname)) {
				if (!dbServerHostname.toLowerCase().startsWith("http")) {
					dbServerHostname = "http://" + dbServerHostname;
				}
				if (dbServerHostname.toLowerCase().startsWith("https://") && dbServerPort == 443) {
					dbServerPort = -1;
				} else if (dbServerHostname.toLowerCase().startsWith("http://") && dbServerPort == 80) {
					dbServerPort = -1;
				}
				return "jdbc:hsqldb:" + dbServerHostname + (dbServerPort <= 0 ? "" : ":" + dbServerPort) + "/" + dbName;
			} else {
				return "jdbc:hsqldb:mem:" + dbName;
			}
		} else if (DbVendor.Cassandra == dbVendor) {
			return "jdbc:cassandra://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + "/" + dbName + "?primarydc=DC1&backupdc=DC2&consistency=QUORUM";
		} else if (DbVendor.MsSQL == dbVendor) {
			if (secureConnection) {
				return "jdbc:sqlserver://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + ";database=" + dbName + ";encrypt=true;trustServerCertificate=true";
			} else {
				return "jdbc:sqlserver://" + dbServerHostname + ":" + (dbServerPort <= 0 ? dbVendor.getDefaultPort() : dbServerPort) + ";database=" + dbName;
			}
		} else {
			throw new Exception("Unknown database vendor");
		}
	}

	/**
	 * Creates a new file database and opens a connection to it. Only SQLite, Derby and HSQL are supported.
	 *
	 * @param dbVendor database vendor (SQLite, Derby or HSQL)
	 * @param dbPath path of the database file or directory ("~" is replaced by the user's home directory)
	 * @return connection to the new database
	 * @throws Exception if the vendor is not supported, the database already exists or cannot be created
	 */
	public static Connection createNewDatabase(final DbVendor dbVendor, String dbPath) throws Exception {
		if (dbVendor == null) {
			throw new Exception("Unknown database vendor");
		}

		if (dbVendor == DbVendor.Derby) {
			// Prevent creation of file "derby.log"
			System.setProperty("derby.stream.error.field", "de.soderer.utilities.db.DbUtilities.DEV_NULL");
		}

		Class.forName(dbVendor.getDriverClassName());

		if (dbVendor == DbVendor.SQLite) {
			dbPath = Utilities.replaceUsersHome(dbPath);
			if (new File(dbPath).exists()) {
				throw new Exception("SQLite database file '" + dbPath + "' already exists");
			}
			return DriverManager.getConnection(generateUrlConnectionString(dbVendor, "", 0, dbPath, false, null, null, null));
		} else if (dbVendor == DbVendor.Derby) {
			dbPath = Utilities.replaceUsersHome(dbPath);
			if (new File(dbPath).exists()) {
				throw new Exception("Derby database directory '" + dbPath + "' already exists");
			}
			return DriverManager.getConnection(generateUrlConnectionString(dbVendor, "", 0, dbPath, false, null, null, null) + ";create=true");
		} else if (dbVendor == DbVendor.HSQL) {
			dbPath = Utilities.replaceUsersHome(dbPath);
			if (dbPath.startsWith("/")) {
				if (Utilities.getFilesByPattern(new File(dbPath.substring(0, dbPath.lastIndexOf("/"))), dbPath.substring(dbPath.lastIndexOf("/") + 1).replace(".", "\\.") + "\\..*", false).size() > 0) {
					throw new Exception("HSQL database '" + dbPath + "' already exists");
				}
			}

			// Logger must be kept in a local variable for making it work
			final Logger dbLogger = Logger.getLogger("hsqldb.db");
			dbLogger.setLevel(Level.WARNING);

			return DriverManager.getConnection(generateUrlConnectionString(dbVendor, "", 0, dbPath, false, null, null, null));
		} else {
			throw new Exception("Invalid database vendor '" + dbVendor.toString() + "'. Only SQLite, HSQL or Derby database can be created this way.");
		}
	}

	/**
	 * Deletes a file database. Only SQLite, Derby and HSQL file databases are supported.
	 * A Derby database is shut down before.
	 *
	 * @param dbVendor database vendor (SQLite, Derby or HSQL)
	 * @param dbPath path of the database file or directory ("~" is replaced by the user's home directory)
	 * @return true if the database was deleted, false if it did not exist
	 * @throws Exception if the vendor is not supported or a database file cannot be deleted
	 */
	public static boolean deleteDatabase(final DbVendor dbVendor, String dbPath) throws Exception {
		if (dbVendor == null) {
			throw new Exception("Unknown database vendor");
		}

		if (dbVendor == DbVendor.SQLite) {
			dbPath = Utilities.replaceUsersHome(dbPath);
			if (new File(dbPath).exists()) {
				if (!new File(dbPath).delete()) {
					throw new Exception("Cannot delete SQLite database file '" + dbPath + "'");
				}
				return true;
			} else {
				return false;
			}
		} else if (dbVendor == DbVendor.Derby) {
			dbPath = Utilities.replaceUsersHome(dbPath);
			if (new File(dbPath).exists()) {
				try {
					DbUtilities.shutDownDerbyDb(dbPath);
				} catch (@SuppressWarnings("unused") final Exception e) {
					// do nothing
				}
				return Utilities.delete(new File(dbPath));
			} else {
				return false;
			}
		} else if (dbVendor == DbVendor.HSQL) {
			dbPath = Utilities.replaceUsersHome(dbPath);
			if (dbPath.startsWith("/")) {
				deleteHsqlDatabaseFiles(new File(dbPath.substring(0, dbPath.lastIndexOf("/"))), dbPath.substring(dbPath.lastIndexOf("/") + 1));
				return true;
			} else if (dbPath.matches(".\\:\\\\.*")) {
				deleteHsqlDatabaseFiles(new File(dbPath.substring(0, dbPath.lastIndexOf("\\"))), dbPath.substring(dbPath.lastIndexOf("\\") + 1));
				return true;
			} else {
				return false;
			}
		} else {
			throw new Exception("Invalid database vendor '" + dbVendor.toString() + "'. Only SQLite, HSQL or Derby database can be deleted this way.");
		}
	}

	/**
	 * Deletes all files of a HSQL file database. A HSQL file database consists of several files named
	 * {@code <basename>.<extension>} (e.g. ".properties", ".script", ".log", ".data", ".lck") and an optional
	 * directory {@code <basename>.tmp}. Only these entries are deleted, other databases with the same name prefix
	 * (e.g. "test2.script" for basename "test") are left untouched.
	 *
	 * @param baseDirectory directory containing the database files
	 * @param basename name of the database without extension
	 * @throws Exception if the directory cannot be read or a file cannot be deleted
	 */
	private static void deleteHsqlDatabaseFiles(final File baseDirectory, final String basename) throws Exception {
		final File[] files = baseDirectory.listFiles();
		if (files == null) {
			throw new Exception("Cannot read database directory '" + baseDirectory.getAbsolutePath() + "'");
		}
		for (final File fileToDelete : files) {
			if (fileToDelete.getName().startsWith(basename + ".")) {
				if (!Utilities.delete(fileToDelete)) {
					throw new Exception("Cannot delete database file '" + fileToDelete.getAbsolutePath() + "'");
				}
			}
		}
	}

	/**
	 * Opens a new database connection.
	 * <p>
	 * For secure connections without truststore, Oracle uses a temporary truststore containing the server's
	 * certificate (trust on first use), MySQL and MariaDB trust the server certificate without validation.
	 * For MySQL and MariaDB the truststore is set as JVM wide system property.
	 * If the connection fails, a plain TCP connection test is done to give a more detailed error message.
	 *
	 * @param dbDefinition connection parameters
	 * @param retryOnError true to retry once on a {@link SQLRecoverableException}
	 * @return new database connection
	 * @throws DbNotExistsException if a file database does not exist
	 * @throws Exception if parameters are missing or invalid, the JDBC driver is not available or the connection fails
	 */
	public static Connection createConnection(final DbConnectionDefinition dbDefinition, final boolean retryOnError) throws Exception {
		final DbVendor dbVendor = dbDefinition.getDbVendor();
		final String hostnameAndPort = dbDefinition.getHostnameAndPort();
		String dbName = dbDefinition.getDbName();
		final String userName = dbDefinition.getUsername();
		final char[] password = dbDefinition.getPassword();
		final boolean secureConnection = dbDefinition.isSecureConnection();
		File trustStoreFile = dbDefinition.getTrustStoreFile();
		final char[] trustStorePassword = dbDefinition.getTrustStorePassword();

		if (dbVendor == null) {
			throw new Exception("Unknown database vendor");
		} else if (Utilities.isEmpty(hostnameAndPort) && dbVendor != DbVendor.HSQL && dbVendor != DbVendor.SQLite && dbVendor != DbVendor.Derby) {
			throw new Exception("Cannot create database connection: Missing hostname");
		} else if (Utilities.isEmpty(dbName)) {
			throw new Exception("Cannot create database connection: Missing dbName");
		} else if (trustStoreFile != null && !trustStoreFile.exists()) {
			throw new Exception("Cannot create database connection: Configured trustStoreFile '" + trustStoreFile.getAbsolutePath() + "' does not exist");
		}

		if (secureConnection && dbVendor != DbVendor.Oracle && dbVendor != DbVendor.MySQL && dbVendor != DbVendor.MariaDB && dbVendor != DbVendor.MsSQL) {
			throw new Exception("Secure connection is only supported for database vendors Oracle, MySQL, MariaDB, MsSQL");
		}

		try {
			if (dbVendor == DbVendor.Derby) {
				// Prevent creation of file "derby.log"
				System.setProperty("derby.stream.error.field", "de.soderer.utilities.db.DbUtilities.DEV_NULL");
			}

			Class.forName(dbVendor.getDriverClassName());
		} catch (final Exception e) {
			throw new Exception("Cannot create database connection, caused by unknown DriverClassName: " + e.getMessage());
		}

		try {
			Connection connection;
			if (dbVendor == DbVendor.SQLite) {
				dbName = Utilities.replaceUsersHome(dbName);
				if (!new File(dbName).exists()) {
					throw new DbNotExistsException("SQLite database file '" + dbName + "' is not available");
				} else if (!new File(dbName).isFile()) {
					throw new Exception("SQLite database file '" + dbName + "' is not a file");
				}
				connection = DriverManager.getConnection(generateUrlConnectionString(dbVendor, "", 0, dbName, false, null, null, null));
			} else if (dbVendor == DbVendor.Derby) {
				dbName = Utilities.replaceUsersHome(dbName);
				if (!new File(dbName).exists()) {
					throw new DbNotExistsException("Derby database directory '" + dbName + "' is not available");
				} else if (!new File(dbName).isDirectory()) {
					throw new Exception("Derby database directory '" + dbName + "' is not a directory");
				}
				connection = DriverManager.getConnection(generateUrlConnectionString(dbVendor, "", 0, dbName, false, null, null, null));
			} else if (dbVendor == DbVendor.HSQL) {
				dbName = Utilities.replaceUsersHome(dbName);
				if (dbName.startsWith("/")) {
					if (Utilities.getFilesByPattern(new File(dbName.substring(0, dbName.lastIndexOf("/"))), dbName.substring(dbName.lastIndexOf("/") + 1).replace(".", "\\.") + "\\..*", false).size() <= 0) {
						throw new DbNotExistsException("HSQL database '" + dbName + "' is not available");
					}
				}
				int port = dbVendor.getDefaultPort();
				String host = "";
				if (Utilities.isNotBlank(hostnameAndPort)) {
					final String[] hostParts = hostnameAndPort.split(":");
					if (hostParts.length == 2) {
						try {
							port = Integer.parseInt(hostParts[1]);
						} catch (@SuppressWarnings("unused") final Exception e) {
							throw new Exception("Invalid port: " + hostParts[1]);
						}
					}
					host = hostParts[0];
				}

				// Logger must be kept in a local variable for making it work
				final Logger dbLogger = Logger.getLogger("hsqldb.db");
				dbLogger.setLevel(Level.WARNING);

				connection = DriverManager.getConnection(generateUrlConnectionString(dbVendor, host, port, dbName, false, null, null, null), (Utilities.isNotEmpty(userName) ? userName : "SA"), (password != null ? new String(password) : ""));
			} else {
				int port;
				final String[] hostParts = hostnameAndPort.split(":");
				if (hostParts.length == 2) {
					try {
						port = Integer.parseInt(hostParts[1]);
					} catch (@SuppressWarnings("unused") final Exception e) {
						throw new Exception("Invalid port: " + hostParts[1]);
					}
				} else {
					port = dbVendor.getDefaultPort();
				}

				//				if (dbVendor == DbVendor.Oracle && new File("/dev/urandom").exists()) {
				//					// Set the alternative random generator to improve the connection creation speed, which may even cause a "I/O-Error: Connection reset"-error on low performance systems
				//					System.setProperty("java.security.egd", "file:///dev/./urandom");
				//
				//					// Alternatively you can change the file $JAVA_HOME/jre/lib/security/java.security and add following line:
				//					// securerandom.source=file:/dev/./urandom
				//				}

				if (!secureConnection) {
					try {
						if (userName != null && password != null) {
							connection = DriverManager.getConnection(generateUrlConnectionString(dbVendor, hostParts[0], port, dbName, false, null, null, null), userName, new String(password));
						} else {
							connection = DriverManager.getConnection(generateUrlConnectionString(dbVendor, hostParts[0], port, dbName, false, null, null, null));
						}
					} catch (final Exception e) {
						if (retryOnError && (e instanceof SQLRecoverableException || e.getCause() instanceof SQLRecoverableException)) {
							if (userName != null && password != null) {
								connection = DriverManager.getConnection(generateUrlConnectionString(dbVendor, hostParts[0], port, dbName, false, null, null, null), userName, new String(password));
							} else {
								connection = DriverManager.getConnection(generateUrlConnectionString(dbVendor, hostParts[0], port, dbName, false, null, null, null));
							}
						} else {
							throw e;
						}
					}
				} else {
					if (trustStoreFile != null) {
						final Properties props = new Properties();
						if (dbVendor == DbVendor.Oracle) {
							props.setProperty("javax.net.ssl.trustStore", trustStoreFile.getAbsolutePath());
							props.setProperty("javax.net.ssl.trustStoreType", "JKS");
							if (trustStorePassword != null && trustStorePassword.length > 0) {
								props.setProperty("javax.net.ssl.trustStorePassword", new String(trustStorePassword));
							}
						} else {
							// MariaDB and MySQLDB ignore trustStore settings in the temporary properties, so system properties must be used
							System.setProperty("javax.net.ssl.trustStore", trustStoreFile.getAbsolutePath());
							System.setProperty("javax.net.ssl.trustStoreType", "JKS");
							if (trustStorePassword != null && trustStorePassword.length > 0) {
								System.setProperty("javax.net.ssl.trustStorePassword", new String(trustStorePassword));
							}
						}

						// Properties is a Hashtable and does not accept null values
						if (userName != null) {
							props.setProperty("user", userName);
						}
						if (password != null) {
							props.setProperty("password", new String(password));
						}

						connection = DriverManager.getConnection(generateUrlConnectionString(dbVendor, hostParts[0], port, dbName, true, trustStoreFile, trustStorePassword, null), props);
					} else {
						final Properties props = new Properties();
						// Properties is a Hashtable and does not accept null values
						if (userName != null) {
							props.setProperty("user", userName);
						}
						if (password != null) {
							props.setProperty("password", new String(password));
						}

						String temporaryTrustStoreFilePath = null;
						if (dbVendor == DbVendor.Oracle) {
							// Create a temporary trustStore
							temporaryTrustStoreFilePath = Files.createTempFile("TempTrustStore", ".jks").toString();
							new File(temporaryTrustStoreFilePath).delete();
							trustStoreFile = new File(temporaryTrustStoreFilePath);
							Utilities.createTrustStoreFile(hostParts[0], port, trustStoreFile, null, null);

							props.setProperty("javax.net.ssl.trustStore", trustStoreFile.getAbsolutePath());
							props.setProperty("javax.net.ssl.trustStoreType", "JKS");
							if (trustStorePassword != null && trustStorePassword.length > 0) {
								props.setProperty("javax.net.ssl.trustStorePassword", new String(trustStorePassword));
							}
						}

						try {
							connection = DriverManager.getConnection(generateUrlConnectionString(dbVendor, hostParts[0], port, dbName, true, trustStoreFile, trustStorePassword, null), props);
						} finally {
							if (dbVendor == DbVendor.Oracle) {
								// Remove temporary trustStore
								new File(temporaryTrustStoreFilePath).delete();
							}
						}
					}
				}

				//				if (dbVendor == DbVendor.Oracle && new File("/dev/random").exists()) {
				//					// Reset the alternative random generator
				//					System.clearProperty("java.security.egd");
				//				}
			}

			if (dbVendor == DbVendor.Cassandra) {
				try (Statement statement = connection.createStatement()) {
					statement.execute("USE " + dbName);
				}
			}

			return connection;
		} catch (final DbNotExistsException e) {
			throw e;
		} catch (final Exception e) {
			try {
				int port;
				if (Utilities.isNotEmpty(hostnameAndPort)) {
					if (hostnameAndPort.contains(":")) {
						try {
							port = Integer.parseInt(hostnameAndPort.substring(hostnameAndPort.indexOf(":") + 1));
						} catch (@SuppressWarnings("unused") final Exception e1) {
							throw new Exception("Invalid port: " + hostnameAndPort.substring(hostnameAndPort.indexOf(":") + 1));
						}
						Utilities.testConnection(hostnameAndPort.substring(0, hostnameAndPort.indexOf(":")), port);
					} else {
						port = dbVendor.getDefaultPort();
						Utilities.testConnection(hostnameAndPort, port);
					}
				}

				// No Exception from testConnection, so it must be some other problem
				throw new Exception("Cannot create database connection: " + e.getMessage(), e);
			} catch (final Exception e1) {
				throw new Exception("Cannot create database connection, caused by Url (" + hostnameAndPort + "): " + e1.getMessage(), e1);
			}
		}
	}

	/**
	 * Converts a SQLite DATE value into a LocalDate. SQLite has no real date type, so dates are stored as
	 * milliseconds (Long) or in several String formats (ISO-8601 with or without offset, "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd").
	 *
	 * @param valueObject value read from SQLite
	 * @return date value, or null if valueObject is null
	 * @throws Exception if the value cannot be parsed
	 */
	public static LocalDate extractSqliteLocalDate(final Object valueObject) throws Exception {
		if (valueObject == null) {
			return null;
		} else if (valueObject instanceof Long) {
			// java.util.Date: 1670968004453 (Long)
			return Instant.ofEpochMilli(((Long) valueObject)).atZone(ZoneId.systemDefault()).toLocalDate();
		} else if (valueObject instanceof String) {
			String valueString = (String) valueObject;
			if (valueString.contains("[")) {
				valueString = valueString.substring(0, valueString.indexOf(("[")));
			}

			final String[] datePatterns = new String[] {
					// ZonedDateTime: "2022-12-13T22:46:44.463491400+01:00[Europe/Berlin]" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSz",

					// ZonedDateTime: "2022-12-13T22:46:44.463491+01:00[Europe/Berlin]" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSSSSSz",

					// ZonedDateTime: "2022-12-13T22:46:44.463+01:00[Europe/Berlin]" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSSz",

					// ZonedDateTime: "2022-12-13T22:46:44+01:00[Europe/Berlin]" (String)
					"yyyy-MM-dd'T'HH:mm:ssz",

					// LocalDateTime: "2022-12-13T22:46:44.460515" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSSSSS",

					// LocalDateTime: "2022-12-13T22:46:44.460" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSS",

					// LocalDateTime: "2022-12-13T22:46:44" (String)
					"yyyy-MM-dd'T'HH:mm:ss",

					// CURRENT_TIMESTAMP: "2022-12-13 21:46:44" (String), CURRENT_TIMESTAMP uses UTC timezone by default
					"yyyy-MM-dd HH:mm:ss",

					// LocalDate: "2022-12-13" (String)
					// CURRENT_DATE: "2022-12-13" (String), CURRENT_DATE uses UTC timezone by default
					"yyyy-MM-dd"
			};

			// ISO formats with any number of fraction digits (0-9), e.g. "2022-12-13T22:46:44.4605153" or "2022-12-13T22:46:44.46+01:00".
			// The pattern letter "n" (nano-of-second) must not be used for this, because it would read ".460515" as 460515 nanoseconds.
			final LocalDateTime isoValue = parseIsoLocalDateTime(valueString);
			if (isoValue != null) {
				return isoValue.toLocalDate();
			}

			for (final String pattern : datePatterns) {
				try {
					return Utilities.parseLocalDate(pattern, valueString);
				} catch(@SuppressWarnings("unused") final DateTimeParseException e) {
					// try next pattern
				}
			}
			throw new Exception("Unparseable value for data type DATE: " + ((String) valueObject));
		} else {
			throw new Exception("Unparseable value type for data type DATE");
		}
	}

	/**
	 * Converts a SQLite TIMESTAMP value into a LocalDateTime. SQLite has no real date type, so timestamps are stored as
	 * milliseconds (Long) or in several String formats (ISO-8601 with or without offset, "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd").
	 * An offset or zone is ignored, the local date time is returned as written.
	 *
	 * @param valueObject value read from SQLite
	 * @return date time value, or null if valueObject is null
	 * @throws Exception if the value cannot be parsed
	 */
	public static LocalDateTime extractSqliteLocalDateTime(final Object valueObject) throws Exception {
		if (valueObject == null) {
			return null;
		} else if (valueObject instanceof Long) {
			// java.util.Date: 1670968004453 (Long)
			return Instant.ofEpochMilli(((Long) valueObject)).atZone(ZoneId.systemDefault()).toLocalDateTime();
		} else if (valueObject instanceof String) {
			String valueString = (String) valueObject;
			if (valueString.contains("[")) {
				valueString = valueString.substring(0, valueString.indexOf(("[")));
			}

			final String[] dateTimePatterns = new String[] {
					// ZonedDateTime: "2022-12-13T22:46:44.463491400+01:00[Europe/Berlin]" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSz",

					// ZonedDateTime: "2022-12-13T22:46:44.463491+01:00[Europe/Berlin]" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSSSSSz",

					// ZonedDateTime: "2022-12-13T22:46:44.463+01:00[Europe/Berlin]" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSSz",

					// ZonedDateTime: "2022-12-13T22:46:44+01:00[Europe/Berlin]" (String)
					"yyyy-MM-dd'T'HH:mm:ssz",

					// LocalDateTime: "2022-12-13T22:46:44.460515" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSSSSS",

					// LocalDateTime: "2022-12-13T22:46:44.460" (String)
					"yyyy-MM-dd'T'HH:mm:ss.SSS",

					// LocalDateTime: "2022-12-13T22:46:44" (String)
					"yyyy-MM-dd'T'HH:mm:ss",

					// CURRENT_TIMESTAMP: "2022-12-13 21:46:44" (String), CURRENT_TIMESTAMP uses UTC timezone by default
					"yyyy-MM-dd HH:mm:ss"
			};

			// ISO formats with any number of fraction digits (0-9), e.g. "2022-12-13T22:46:44.4605153" or "2022-12-13T22:46:44.46+01:00".
			// The pattern letter "n" (nano-of-second) must not be used for this, because it would read ".460515" as 460515 nanoseconds.
			final LocalDateTime isoValue = parseIsoLocalDateTime(valueString);
			if (isoValue != null) {
				return isoValue;
			}

			for (final String pattern : dateTimePatterns) {
				try {
					return Utilities.parseLocalDateTime(pattern, valueString);
				} catch(@SuppressWarnings("unused") final DateTimeParseException e) {
					// try next pattern
				}
			}

			if (valueString.length() == 10) {
				// LocalDate: "2022-12-13" (String)
				// CURRENT_DATE: "2022-12-13" (String), CURRENT_DATE uses UTC timezone by default
				return Utilities.parseLocalDateTime("yyyy-MM-dd'T'HH:mm:ss", valueString + "T00:00:00");
			} else {
				throw new Exception("Unparseable value for data type TIMESTAMP: " + ((String) valueObject));
			}
		} else {
			throw new Exception("Unparseable value type for data type TIMESTAMP");
		}
	}

	/**
	 * Parses an ISO-8601 date time value with or without offset and with any number of fraction digits.
	 * An offset is ignored, the local date time part is returned as written.
	 *
	 * @param value value to parse
	 * @return parsed value or null if the value is no ISO-8601 date time
	 */
	private static LocalDateTime parseIsoLocalDateTime(final String value) {
		try {
			return LocalDateTime.parse(value);
		} catch (@SuppressWarnings("unused") final DateTimeParseException e) {
			try {
				return OffsetDateTime.parse(value).toLocalDateTime();
			} catch (@SuppressWarnings("unused") final DateTimeParseException e2) {
				return null;
			}
		}
	}

	/**
	 * Detects the database vendor of a DataSource by its product name.
	 *
	 * @param dataSource data source
	 * @return database vendor
	 * @throws Exception if no connection can be opened or the vendor is unknown
	 */
	public static DbVendor getDbVendor(final DataSource dataSource) throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			return getDbVendor(connection);
		} catch (final SQLException e) {
			throw new Exception("Cannot check database vendor: " + e.getMessage(), e);
		}
	}

	/**
	 * Detects the database vendor of a connection by its product name.
	 *
	 * @param connection database connection
	 * @return database vendor
	 * @throws Exception if the vendor is unknown or cannot be detected
	 */
	public static DbVendor getDbVendor(final Connection connection) throws Exception {
		try {
			final DatabaseMetaData databaseMetaData = connection.getMetaData();
			if (databaseMetaData != null) {
				final String productName = databaseMetaData.getDatabaseProductName();
				if (productName != null && productName.toLowerCase().contains("oracle")) {
					return DbVendor.Oracle;
				} else if (productName != null && productName.toLowerCase().contains("mysql")) {
					return DbVendor.MySQL;
				} else if (productName != null && productName.toLowerCase().contains("maria")) {
					return DbVendor.MariaDB;
				} else if (productName != null && productName.toLowerCase().contains("postgres")) {
					return DbVendor.PostgreSQL;
				} else if (productName != null && productName.toLowerCase().contains("sqlite")) {
					return DbVendor.SQLite;
				} else if (productName != null && productName.toLowerCase().contains("derby")) {
					return DbVendor.Derby;
				} else if (productName != null && productName.toLowerCase().contains("hsql")) {
					return DbVendor.HSQL;
				} else if (productName != null && productName.toLowerCase().contains("firebird")) {
					return DbVendor.Firebird;
				} else if (productName != null && productName.toLowerCase().contains("cassandra")) {
					return DbVendor.Cassandra;
				} else if (productName != null && productName.toLowerCase().contains("microsoft")) {
					return DbVendor.MsSQL;
				} else {
					throw new Exception("Unknown database vendor: " + productName);
				}
			} else {
				throw new Exception("Undetectable database vendor");
			}
		} catch (final SQLException e) {
			throw new Exception("Error while detecting database vendor: " + e.getMessage(), e);
		}
	}

	/**
	 * Returns the JDBC url of a DataSource.
	 *
	 * @param dataSource data source
	 * @return JDBC url, or null if not available
	 * @throws SQLException if no connection can be opened
	 */
	public static String getDbUrl(final DataSource dataSource) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			return getDbUrl(connection);
		}
	}

	/**
	 * Returns the JDBC url of a connection.
	 *
	 * @param connection database connection
	 * @return JDBC url, or null if not available
	 */
	public static String getDbUrl(final Connection connection) {
		try {
			final DatabaseMetaData databaseMetaData = connection.getMetaData();
			if (databaseMetaData != null) {
				return databaseMetaData.getURL();
			} else {
				return null;
			}
		} catch (@SuppressWarnings("unused") final SQLException e) {
			return null;
		}
	}

	/**
	 * Checks if a table contains all given columns (case-insensitive).
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param columns column names, may be escaped by vendor specific quotes
	 * @return true if all columns exist
	 * @throws Exception if the table does not exist or its columns cannot be read
	 */
	public static boolean checkTableAndColumnsExist(final Connection connection, final String tableName, final String... columns) throws Exception {
		return checkTableAndColumnsExist(connection, tableName, false, columns);
	}

	/**
	 * Checks if a table contains all given columns (case-insensitive).
	 *
	 * @param dataSource data source
	 * @param tableName name of the table
	 * @param throwExceptionOnError true to throw an exception instead of returning false for a missing column
	 * @param columns column names, may be escaped by vendor specific quotes
	 * @return true if all columns exist
	 * @throws Exception if a column is missing (and throwExceptionOnError is set) or the columns cannot be read
	 */
	public static boolean checkTableAndColumnsExist(final DataSource dataSource, final String tableName, final boolean throwExceptionOnError, final String... columns) throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			return checkTableAndColumnsExist(connection, tableName, throwExceptionOnError, columns);
		}
	}

	/**
	 * Checks if a table contains all given columns (case-insensitive).
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param throwExceptionOnError true to throw an exception instead of returning false for a missing column
	 * @param columns column names, may be escaped by vendor specific quotes
	 * @return true if all columns exist
	 * @throws Exception if a column is missing (and throwExceptionOnError is set) or the columns cannot be read
	 */
	public static boolean checkTableAndColumnsExist(final Connection connection, final String tableName, final boolean throwExceptionOnError, final String... columns) throws Exception {
		final DbVendor dbVendor = getDbVendor(connection);
		final CaseInsensitiveSet dbTableColumns = getColumnNames(connection, tableName);
		if (columns != null) {
			for (final String column : columns) {
				if (column != null && !dbTableColumns.contains(unescapeVendorReservedNames(dbVendor, column))) {
					if (throwExceptionOnError) {
						throw new Exception("Column '" + column + "' does not exist in table '" + tableName + "'");
					} else {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * Checks if a table exists by selecting from it.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @return true if the table exists
	 * @throws Exception if no statement can be created
	 */
	public static boolean checkTableExist(final Connection connection, final String tableName) throws Exception {
		return checkTableExist(connection, tableName, false);
	}

	/**
	 * Checks if a table exists by selecting from it.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param throwExceptionOnError true to throw an exception instead of returning false
	 * @return true if the table exists
	 * @throws Exception if the table does not exist (and throwExceptionOnError is set) or no statement can be created
	 */
	public static boolean checkTableExist(final Connection connection, final String tableName, final boolean throwExceptionOnError) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.setFetchSize(100);
			try (ResultSet resultSet = statement.executeQuery("SELECT * FROM " + tableName + " WHERE 1 = 0")) {
				return true;
			} catch (@SuppressWarnings("unused") final Exception e) {
				if (throwExceptionOnError) {
					throw new Exception("Table '" + tableName + "' does not exist");
				} else {
					return false;
				}
			}
		}
	}

	/**
	 * Calls an Oracle stored procedure and returns everything it wrote via DBMS_OUTPUT.
	 * {@link java.util.Date} parameters are converted to {@link java.sql.Date}.
	 *
	 * @param connection Oracle database connection
	 * @param procedureName name of the stored procedure
	 * @param parameters parameters of the procedure, may contain null values
	 * @return output of DBMS_OUTPUT
	 * @throws SQLException if the procedure call fails
	 */
	public static String callStoredProcedureWithDbmsOutput(final Connection connection, final String procedureName, final Object... parameters) throws SQLException {
		try (CallableStatement callableStatement = connection.prepareCall("begin dbms_output.enable(:1); end;")) {
			callableStatement.setLong(1, 10000);
			callableStatement.executeUpdate();
		}

		if (parameters != null) {
			try (CallableStatement callableStatement = connection.prepareCall("{call " + procedureName + "(" + Utilities.repeat("?", parameters.length, ", ") + ")}")) {
				for (int i = 0; i < parameters.length; i++) {
					// Convert java.util.Date without changing the caller's parameter array; null parameters are allowed
					if (parameters[i] != null && parameters[i].getClass() == Date.class) {
						callableStatement.setObject(i + 1, new java.sql.Date(((Date) parameters[i]).getTime()));
					} else {
						callableStatement.setObject(i + 1, parameters[i]);
					}
				}
				callableStatement.execute();
			}
		} else {
			try (CallableStatement callableStatement = connection.prepareCall("{call " + procedureName + "()}")) {
				callableStatement.execute();
			}
		}

		final StringBuffer dbmsOutput = new StringBuffer(1024);
		try (CallableStatement callableStatement = connection.prepareCall(
				"declare "
						+ "    l_line varchar2(255); "
						+ "    l_done number; "
						+ "    l_buffer long; "
						+ "begin "
						+ "  loop "
						+ "    exit when length(l_buffer)+255 > :maxbytes OR l_done = 1; "
						+ "    dbms_output.get_line( l_line, l_done ); "
						+ "    l_buffer := l_buffer || l_line || chr(10); "
						+ "  end loop; "
						+ " :done := l_done; "
						+ " :buffer := l_buffer; "
						+ "end;")) {
			callableStatement.registerOutParameter(2, Types.INTEGER);
			callableStatement.registerOutParameter(3, Types.VARCHAR);
			while (true) {
				callableStatement.setInt(1, 32000);
				callableStatement.executeUpdate();
				dbmsOutput.append(callableStatement.getString(3).trim());
				if (callableStatement.getInt(2) == 1) {
					break;
				}
			}
		}

		try (CallableStatement callableStatement = connection.prepareCall("begin dbms_output.disable; end;")) {
			callableStatement.executeUpdate();
		}

		return dbmsOutput.toString();
	}

	/**
	 * Returns the column names of a table.
	 *
	 * @param dataSource data source
	 * @param tableName name of the table
	 * @return column names (case-insensitive)
	 * @throws Exception if the data source is null or the columns cannot be read
	 */
	public static CaseInsensitiveSet getColumnNames(final DataSource dataSource, final String tableName) throws Exception {
		if (dataSource == null) {
			throw new Exception("Invalid empty dataSource for getColumnNames");
		}

		try (Connection connection = dataSource.getConnection()) {
			return getColumnNames(connection, tableName);
		} catch (final SQLException e) {
			throw new Exception("Cannot read columns for table " + tableName + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Returns the column names of a table.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @return column names (case-insensitive)
	 * @throws Exception if a parameter is empty or the columns cannot be read
	 */
	public static CaseInsensitiveSet getColumnNames(final Connection connection, final String tableName) throws Exception {
		if (connection == null) {
			throw new Exception("Invalid empty connection for getColumnNames");
		} else if (Utilities.isBlank(tableName)) {
			throw new Exception("Invalid empty tableName for getColumnNames");
		} else {
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("SELECT * FROM " + getSQLSafeString(tableName) + " WHERE 1 = 0")) {
				final CaseInsensitiveSet columnNamesList = new CaseInsensitiveSet();
				for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
					columnNamesList.add(resultSet.getMetaData().getColumnName(i));
				}
				return columnNamesList;
			}
		}
	}

	/**
	 * Returns the column types of a table.
	 *
	 * @param dataSource data source
	 * @param tableName name of the table
	 * @return column types by lowercased column names
	 * @throws Exception if a parameter is empty, the vendor is not supported or the columns cannot be read
	 */
	public static CaseInsensitiveMap<DbColumnType> getColumnDataTypes(final DataSource dataSource, final String tableName) throws Exception {
		if (dataSource == null) {
			throw new Exception("Invalid empty dataSource for getColumnDataTypes");
		} else if (Utilities.isBlank(tableName)) {
			throw new Exception("Invalid empty tableName for getColumnDataTypes");
		} else {
			try (Connection connection = dataSource.getConnection()) {
				return getColumnDataTypes(connection, tableName);
			}
		}
	}

	/**
	 * Returns the column types of a table, read from the vendor specific dictionary views.
	 * The default values of the returned types are not set, see {@link #getColumnDefaultValues(Connection, String)}.
	 *
	 * @param connection database connection
	 * @param tableName name of the table, optionally with schema prefix for PostgreSQL and keyspace prefix for Cassandra
	 * @return column types by lowercased column names
	 * @throws Exception if a parameter is empty, the vendor is not supported or the columns cannot be read
	 */
	public static CaseInsensitiveMap<DbColumnType> getColumnDataTypes(final Connection connection, final String tableName) throws Exception {
		if (connection == null) {
			throw new Exception("Invalid empty connection for getColumnDataTypes");
		} else if (Utilities.isBlank(tableName)) {
			throw new Exception("Invalid empty tableName for getColumnDataTypes");
		} else {
			final CaseInsensitiveMap<DbColumnType> returnMap = new CaseInsensitiveMap<>();
			final DbVendor dbVendor = getDbVendor(connection);
			if (DbVendor.Oracle == dbVendor) {
				// Watchout: Oracle's timestamp datatype is "TIMESTAMP(6)", so remove the bracket value
				final String sql = "SELECT column_name, NVL(substr(data_type, 1, instr(data_type, '(') - 1), data_type) AS data_type, data_length, data_precision, data_scale, nullable FROM user_tab_columns WHERE LOWER(table_name) = LOWER(?)";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName);
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							int characterLength = resultSet.getInt("data_length");
							if (resultSet.wasNull()) {
								characterLength = -1;
							}
							int numericPrecision = resultSet.getInt("data_precision");
							if (resultSet.wasNull()) {
								numericPrecision = -1;
							}
							int numericScale = resultSet.getInt("data_scale");
							if (resultSet.wasNull()) {
								numericScale = -1;
							}
							final boolean isNullable = "y".equalsIgnoreCase(resultSet.getString("nullable"));

							// TODO AutoIncrements will be introduced with Oracle 12
							returnMap.put(resultSet.getString("column_name"), new DbColumnType(resultSet.getString("data_type"), characterLength, numericPrecision, numericScale, isNullable, false, null));
						}
					}
				}
			} else if (DbVendor.MySQL == dbVendor) {
				final String sql = "SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable, extra FROM information_schema.columns WHERE LOWER(table_schema) = SCHEMA() AND LOWER(table_name) = LOWER(?)";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName);
					try (ResultSet resultSet = preparedStatement.executeQuery() ) {
						while (resultSet.next()) {
							long characterLength = resultSet.getLong("character_maximum_length");
							if (resultSet.wasNull()) {
								characterLength = -1;
							}
							int numericPrecision = resultSet.getInt("numeric_precision");
							if (resultSet.wasNull()) {
								numericPrecision = -1;
							}
							int numericScale = resultSet.getInt("numeric_scale");
							if (resultSet.wasNull()) {
								numericScale = -1;
							}
							final boolean isNullable = "yes".equalsIgnoreCase(resultSet.getString("is_nullable"));
							final boolean isAutoIncrement = "auto_increment".equalsIgnoreCase(resultSet.getString("extra"));

							returnMap.put(resultSet.getString("column_name"), new DbColumnType(resultSet.getString("data_type"), characterLength, numericPrecision, numericScale, isNullable, isAutoIncrement, null));
						}
					}
				}
			} else if (DbVendor.MariaDB == dbVendor) {
				final String sql = "SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable, extra FROM information_schema.columns WHERE LOWER(table_schema) = SCHEMA() AND LOWER(table_name) = LOWER(?)";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName);
					try (ResultSet resultSet = preparedStatement.executeQuery() ) {
						while (resultSet.next()) {
							long characterLength = resultSet.getLong("character_maximum_length");
							if (resultSet.wasNull()) {
								characterLength = -1;
							}
							int numericPrecision = resultSet.getInt("numeric_precision");
							if (resultSet.wasNull()) {
								numericPrecision = -1;
							}
							int numericScale = resultSet.getInt("numeric_scale");
							if (resultSet.wasNull()) {
								numericScale = -1;
							}
							final boolean isNullable = "yes".equalsIgnoreCase(resultSet.getString("is_nullable"));
							final boolean isAutoIncrement = "auto_increment".equalsIgnoreCase(resultSet.getString("extra"));

							returnMap.put(resultSet.getString("column_name"), new DbColumnType(resultSet.getString("data_type"), characterLength, numericPrecision, numericScale, isNullable, isAutoIncrement, null));
						}
					}
				}
			} else if (DbVendor.HSQL == dbVendor) {
				final String sql = "SELECT column_name, type_name, column_size, decimal_digits, is_nullable, is_autoincrement FROM information_schema.system_columns WHERE LOWER(table_name) = LOWER(?)";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName);
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							long characterLength = resultSet.getLong("column_size");
							if (resultSet.wasNull()) {
								characterLength = -1;
							}
							int numericPrecision = resultSet.getInt("column_size");
							if (resultSet.wasNull()) {
								numericPrecision = -1;
							}
							int numericScale = resultSet.getInt("decimal_digits");
							if (resultSet.wasNull()) {
								numericScale = -1;
							}
							final boolean isNullable = "yes".equalsIgnoreCase(resultSet.getString("is_nullable"));
							final boolean isAutoIncrement = "yes".equalsIgnoreCase(resultSet.getString("is_autoincrement"));

							returnMap.put(resultSet.getString("column_name"), new DbColumnType(resultSet.getString("type_name"), characterLength, numericPrecision, numericScale, isNullable, isAutoIncrement, null));
						}
					}
				}
			} else if (DbVendor.Derby == dbVendor) {
				final String sql = "SELECT columnname, columndatatype, autoincrementvalue FROM sys.systables, sys.syscolumns WHERE tableid = referenceid AND LOWER(tablename) = LOWER(?)";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setString(1, tableName);
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							String type = resultSet.getString("columndatatype");

							boolean isNullable;
							if (Utilities.endsWithIgnoreCase(type, "not null")) {
								isNullable = false;
								type = type.substring(0, type.length() - 8).trim();
							} else {
								isNullable = true;
							}

							final boolean autoincrement = !Utilities.isBlank(resultSet.getString("autoincrementvalue"));

							final DbColumnType typeWithParameters = parseTypeWithParameters(type, isNullable, autoincrement);
							returnMap.put(resultSet.getString("columnname"), typeWithParameters);
						}
					}
				}
			} else if (DbVendor.Firebird == dbVendor) {
				final String sql = "SELECT rf.rdb$field_name, f.rdb$field_type, f.rdb$field_sub_type, f.rdb$field_length, f.rdb$field_scale, f.rdb$null_flag"
						+ " FROM rdb$fields f JOIN rdb$relation_fields rf ON rf.rdb$field_source = f.rdb$field_name WHERE rf.rdb$relation_name = ?";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setString(1, tableName.toUpperCase());
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							long characterLength = resultSet.getLong("rdb$field_length");
							if (resultSet.wasNull()) {
								characterLength = -1;
							}
							int numericPrecision = resultSet.getInt("rdb$field_length");
							if (resultSet.wasNull()) {
								numericPrecision = -1;
							}
							int numericScale = resultSet.getInt("rdb$field_scale");
							if (resultSet.wasNull()) {
								numericScale = -1;
							}
							final boolean isNullable = resultSet.getObject("rdb$null_flag") == null;

							String dataType;
							switch (resultSet.getInt("rdb$field_type")) {
								case 7: dataType = "SMALLINT";
								break;
								case 8: dataType = "INTEGER";
								break;
								case 10: dataType = "FLOAT";
								break;
								case 12: dataType = "DATE";
								break;
								case 13: dataType = "TIME";
								break;
								case 14: dataType = "CHAR";
								break;
								case 16: dataType = "BIGINT";
								break;
								case 27: dataType = "DOUBLE PRECISION";
								break;
								case 35: dataType = "TIMESTAMP";
								break;
								case 37: dataType = "VARCHAR";
								break;
								case 261:
									if (resultSet.getInt("rdb$field_sub_type") == 1) {
										dataType = "CLOB";
									} else {
										dataType = "BLOB";
									}
									break;
								default: dataType = getTypeNameById(resultSet.getInt("rdb$field_type"));
							}

							// TODO check autoincrement
							returnMap.put(resultSet.getString("rdb$field_name").trim(), new DbColumnType(dataType, characterLength, numericPrecision, numericScale, isNullable, false, null));
						}
					}
				}
			} else if (DbVendor.SQLite == dbVendor) {
				boolean hasAutoIncrement = false;
				if (checkTableExist(connection, "sqlite_sequence")) {
					// sqlite_sequence only exists if there is any table with auto_increment
					try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM sqlite_sequence WHERE LOWER(name) = LOWER(?)")) {
						preparedStatement.setFetchSize(100);
						preparedStatement.setString(1, tableName);
						try (ResultSet resultSet = preparedStatement.executeQuery()) {
							if (resultSet.next()) {
								hasAutoIncrement = resultSet.getInt(1) > 0;
							}
						}
					}
				}

				final String sql = "PRAGMA table_info(" + tableName + ")";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							final boolean isNullable = resultSet.getInt("notnull") == 0;
							// Only the primary key can be auto incremented in SQLite
							final boolean isAutoIncrement = hasAutoIncrement && resultSet.getInt("pk") > 0;

							// SQLite columns may be declared without any type, so the type may be empty
							final String type = resultSet.getString("type");
							returnMap.put(resultSet.getString("name"), parseTypeWithParameters(type == null ? "" : type, isNullable, isAutoIncrement));
						}
					}
				}
			} else if (DbVendor.PostgreSQL == dbVendor) {
				String schemaName;
				String tableNameWithoutSchema;
				if (tableName.contains(".")) {
					schemaName = tableName.substring(0, tableName.lastIndexOf("."));
					tableNameWithoutSchema = tableName.substring(tableName.lastIndexOf(".") + 1);
				} else {
					schemaName = null;
					tableNameWithoutSchema = tableName;
				}
				final String sql = "SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable, column_default FROM information_schema.columns"
						+ " WHERE " + (schemaName != null ? "LOWER(table_schema) = LOWER(?) AND " : "") + "LOWER(table_name) = LOWER(?)";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					if (schemaName == null) {
						preparedStatement.setString(1, tableNameWithoutSchema);
					} else {
						preparedStatement.setString(1, schemaName);
						preparedStatement.setString(2, tableNameWithoutSchema);
					}
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							long characterLength = resultSet.getLong("character_maximum_length");
							if (resultSet.wasNull()) {
								characterLength = -1;
							}
							int numericPrecision = resultSet.getInt("numeric_precision");
							if (resultSet.wasNull()) {
								numericPrecision = -1;
							}
							int numericScale = resultSet.getInt("numeric_scale");
							if (resultSet.wasNull()) {
								numericScale = -1;
							}
							final boolean isNullable = "yes".equalsIgnoreCase(resultSet.getString("is_nullable"));

							final String defaultValue = resultSet.getString("column_default");
							boolean isAutoIncrement = false;
							if (defaultValue != null && defaultValue.toLowerCase().startsWith("nextval(")) {
								isAutoIncrement = true;
							}

							returnMap.put(resultSet.getString("column_name"), new DbColumnType(resultSet.getString("data_type"), characterLength, numericPrecision, numericScale, isNullable, isAutoIncrement, null));
						}
					}
				}
			} else if (DbVendor.Cassandra == dbVendor) {
				if (tableName.contains(".")) {
					final String keySpaceName = tableName.substring(0, tableName.indexOf("."));
					final String tableNameInKeySpace = tableName.substring(tableName.indexOf(".") + 1);
					final String sql = "SELECT column_name, kind, type FROM system_schema.columns WHERE keyspace_name = ? AND table_name = ?";
					try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
						preparedStatement.setFetchSize(100);
						preparedStatement.setNString(1, keySpaceName);
						preparedStatement.setNString(2, tableNameInKeySpace);
						try (ResultSet resultSet = preparedStatement.executeQuery() ) {
							while (resultSet.next()) {
								returnMap.put(resultSet.getString("column_name"), new DbColumnType(resultSet.getString("type"), -1, -1, -1, !resultSet.getString("kind").equalsIgnoreCase("partition_key"), false, null));
							}
						}
					}
				} else {
					final String sql = "SELECT keyspace_name, column_name, kind, type FROM system_schema.columns WHERE table_name = ? ALLOW FILTERING";
					try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
						preparedStatement.setFetchSize(100);
						preparedStatement.setNString(1, tableName);
						try (ResultSet resultSet = preparedStatement.executeQuery() ) {
							String keyspace = null;
							while (resultSet.next()) {
								final String nextKeyspace = resultSet.getString("keyspace_name");
								if (keyspace == null) {
									keyspace = nextKeyspace;
								} else if (!keyspace.equals(nextKeyspace)) {
									throw new Exception("Multiple tables for table name '" + tableName + "' found. Please specify keyspace by keyspace_name prefix.");
								}
								returnMap.put(resultSet.getString("column_name"), new DbColumnType(resultSet.getString("type"), -1, -1, -1, !resultSet.getString("kind").equalsIgnoreCase("partition_key"), false, null));
							}
						}
					}
				}
			} else if (DbVendor.MsSQL == dbVendor) {
				final String sql = "SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable,"
						+ " COLUMNPROPERTY(OBJECT_ID(table_schema + '.' + table_name), column_name, 'IsIdentity') AS is_identity"
						+ " FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?)";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName);
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							long characterLength = resultSet.getLong("character_maximum_length");
							if (resultSet.wasNull()) {
								characterLength = -1;
							}
							int numericPrecision = resultSet.getInt("numeric_precision");
							if (resultSet.wasNull()) {
								numericPrecision = -1;
							}
							int numericScale = resultSet.getInt("numeric_scale");
							if (resultSet.wasNull()) {
								numericScale = -1;
							}
							final boolean isNullable = "yes".equalsIgnoreCase(resultSet.getString("is_nullable"));

							final boolean isAutoIncrement = resultSet.getInt("is_identity") == 1;

							returnMap.put(resultSet.getString("column_name"), new DbColumnType(resultSet.getString("data_type"), characterLength, numericPrecision, numericScale, isNullable, isAutoIncrement, null));
						}
					}
				}
			} else {
				throw new Exception("Unsupported database vendor");
			}
			return returnMap;
		}
	}

	/**
	 * Creates a column type from a type declaration like "VARCHAR(100)", "DECIMAL(10,2)" or "INTEGER".
	 * A single parameter is used as character length, two parameters are used as numeric precision and scale.
	 *
	 * @param typeDeclaration type declaration with optional parameters in brackets
	 * @param isNullable true if the column is nullable
	 * @param isAutoIncrement true if the column is auto incremented
	 * @return column type without default value
	 */
	private static DbColumnType parseTypeWithParameters(final String typeDeclaration, final boolean isNullable, final boolean isAutoIncrement) {
		String type = typeDeclaration;
		long characterLength = -1;
		int numericPrecision = -1;
		int numericScale = -1;
		if (type.contains("(") && type.indexOf(")") > type.indexOf("(")) {
			final String[] parameters = type.substring(type.indexOf("(") + 1, type.indexOf(")")).split(",");
			type = type.substring(0, type.indexOf("(")).trim();
			try {
				if (parameters.length >= 2) {
					numericPrecision = Integer.parseInt(parameters[0].trim());
					numericScale = Integer.parseInt(parameters[1].trim());
				} else {
					characterLength = Long.parseLong(parameters[0].trim());
				}
			} catch (@SuppressWarnings("unused") final NumberFormatException e) {
				// Unparseable type parameters (e.g. "VARCHAR(MAX)") are ignored
			}
		}
		return new DbColumnType(type, characterLength, numericPrecision, numericScale, isNullable, isAutoIncrement, null);
	}

	/**
	 * Counts the rows of a table.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @return number of rows
	 * @throws Exception if the table name is empty or the rows cannot be counted
	 */
	public static int getTableEntriesCount(final Connection connection, final String tableName) throws Exception {
		if (Utilities.isBlank(tableName)) {
			throw new Exception("Invalid empty tableName for getTableEntriesNumber");
		} else {
			final String sqlStatmentString = "SELECT COUNT(*) FROM " + getSQLSafeString(tableName);
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery(sqlStatmentString)) {
				if (resultSet.next()) {
					return resultSet.getInt(1);
				} else {
					return 0;
				}
			} catch(final Exception e) {
				throw new Exception("Cannot count lines of table '" + tableName + "': " + sqlStatmentString + "\n" + e.getMessage(), e);
			}
		}
	}

	/**
	 * Checks if a table contains a column (case-insensitive).
	 *
	 * @param dataSource data source
	 * @param tableName name of the table
	 * @param columnName name of the column
	 * @return true if the column exists
	 * @throws Exception if a parameter is empty or the columns cannot be read
	 */
	public static boolean containsColumnName(final DataSource dataSource, final String tableName, final String columnName) throws Exception {
		if (dataSource == null) {
			throw new Exception("Invalid empty dataSource for containsColumnName");
		} else if (Utilities.isBlank(tableName)) {
			throw new Exception("Invalid empty tableName for containsColumnName");
		} else if (Utilities.isBlank(columnName)) {
			throw new Exception("Invalid empty columnName for containsColumnName");
		} else {
			try (Connection connection = dataSource.getConnection();
					Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("SELECT * FROM " + getSQLSafeString(tableName) + " WHERE 1 = 0")) {
				for (int columnIndex = 1; columnIndex <= resultSet.getMetaData().getColumnCount(); columnIndex++) {
					if (resultSet.getMetaData().getColumnName(columnIndex).equalsIgnoreCase(columnName.trim())) {
						return true;
					}
				}
				return false;
			}
		}
	}

	/**
	 * Returns the default value of a column.
	 *
	 * @param dataSource data source
	 * @param tableName name of the table
	 * @param columnName name of the column
	 * @return default value without surrounding quotes, or null if the column has no default value
	 * @throws Exception if a parameter is empty or the default value cannot be read
	 */
	public static String getColumnDefaultValue(final DataSource dataSource, final String tableName, final String columnName) throws Exception {
		if (dataSource == null) {
			throw new Exception("Invalid empty dataSource for getColumnDefaultValue");
		} else if (Utilities.isBlank(tableName)) {
			throw new Exception("Invalid empty tableName for getColumnDefaultValue");
		} else if (Utilities.isBlank(columnName)) {
			throw new Exception("Invalid empty columnName for getColumnDefaultValue");
		} else {
			try (Connection connection = dataSource.getConnection()) {
				return getColumnDefaultValue(connection, tableName, columnName);
			}
		}
	}

	/**
	 * Returns the default value of a column. Supported for Oracle, HSQL, PostgreSQL and vendors with an
	 * information_schema supporting SCHEMA() (MySQL, MariaDB).
	 *
	 * @param connection database connection
	 * @param tableName name of the table, optionally with schema prefix for PostgreSQL
	 * @param columnName name of the column
	 * @return default value without surrounding quotes, or null if the column has no default value
	 * @throws Exception if a parameter is empty or the default value cannot be read
	 */
	public static String getColumnDefaultValue(final Connection connection, final String tableName, final String columnName) throws Exception {
		if (connection == null) {
			throw new Exception("Invalid empty connection for getColumnDefaultValue");
		} else if (Utilities.isBlank(tableName)) {
			throw new Exception("Invalid empty tableName for getColumnDefaultValue");
		} else if (Utilities.isBlank(columnName)) {
			throw new Exception("Invalid empty columnName for getColumnDefaultValue");
		} else {
			final DbVendor dbVendor = getDbVendor(connection);
			if (DbVendor.Oracle == dbVendor) {
				final String sql = "SELECT data_default FROM user_tab_cols WHERE table_name = ? AND column_name = ?";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName.toUpperCase());
					preparedStatement.setNString(2, columnName.toUpperCase());
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						if (resultSet.next()) {
							final String defaultvalue = resultSet.getString(1);
							String returnValue;
							if (defaultvalue == null || "null".equalsIgnoreCase(defaultvalue)) {
								returnValue = null;
							} else if (defaultvalue.startsWith("'") && defaultvalue.endsWith("'")) {
								returnValue = defaultvalue.substring(1, defaultvalue.length() - 1);
							} else {
								returnValue = defaultvalue;
							}
							return returnValue;
						} else {
							return null;
						}
					}
				}
			} else if (DbVendor.HSQL == dbVendor) {
				final String sql = "SELECT column_default FROM information_schema.columns WHERE table_name = ? AND column_name = ?";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName);
					preparedStatement.setNString(2, columnName);
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						if (resultSet.next()) {
							final String returnValue = resultSet.getString(1);
							if ("NULL".equalsIgnoreCase(returnValue)) {
								return null;
							} else {
								return returnValue;
							}
						} else {
							return null;
						}
					}
				}
			} else if (DbVendor.PostgreSQL == dbVendor) {
				String schemaName;
				String tableNameWithoutSchema;
				if (tableName.contains(".")) {
					schemaName = tableName.substring(0, tableName.lastIndexOf("."));
					tableNameWithoutSchema = tableName.substring(tableName.lastIndexOf(".") + 1);
				} else {
					schemaName = null;
					tableNameWithoutSchema = tableName;
				}

				final String sql = "SELECT column_default FROM information_schema.columns WHERE " + (schemaName != null ? "LOWER(table_schema) = LOWER(?) AND " : "") + "table_name = ? AND column_name = ?";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					if (schemaName == null) {
						preparedStatement.setString(1, tableNameWithoutSchema);
						preparedStatement.setString(2, columnName);
					} else {
						preparedStatement.setString(1, schemaName);
						preparedStatement.setString(2, tableNameWithoutSchema);
						preparedStatement.setString(3, columnName);
					}
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						if (resultSet.next()) {
							String defaultvalue = resultSet.getString(1);
							if (defaultvalue != null && defaultvalue.toLowerCase().endsWith("::integer")) {
								defaultvalue = defaultvalue.substring(0, defaultvalue.toLowerCase().indexOf("::integer"));
								if (defaultvalue.trim().startsWith("'") && defaultvalue.trim().endsWith("'")) {
									defaultvalue = defaultvalue.trim().substring(1, defaultvalue.trim().length() - 1);
								}
								return defaultvalue;
							} else if ("NULL".equalsIgnoreCase(defaultvalue)) {
								return null;
							} else {
								return defaultvalue;
							}
						} else {
							return null;
						}
					}
				}
			} else {
				final String sql = "SELECT column_default FROM information_schema.columns WHERE table_schema = (SELECT schema()) AND table_name = ? AND column_name = ?";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName);
					preparedStatement.setNString(2, columnName);
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						if (resultSet.next()) {
							final String returnValue = resultSet.getString(1);
							if ("NULL".equalsIgnoreCase(returnValue)) {
								return null;
							} else {
								return returnValue;
							}
						} else {
							return null;
						}
					}
				}
			}
		}
	}


	/**
	 * Returns the default values of all columns of a table.
	 *
	 * @param dataSource data source
	 * @param tableName name of the table
	 * @return default values by lowercased column names (null values for columns without default value)
	 * @throws Exception if a parameter is empty or the default values cannot be read
	 */
	public static CaseInsensitiveMap<Object> getColumnDefaultValues(final DataSource dataSource, final String tableName) throws Exception {
		if (dataSource == null) {
			throw new Exception("Invalid empty dataSource for getColumnDefaultValue");
		} else if (Utilities.isBlank(tableName)) {
			throw new Exception("Invalid empty tableName for getColumnDefaultValue");
		} else {
			try (Connection connection = dataSource.getConnection()) {
				return getColumnDefaultValues(connection, tableName);
			}
		}
	}

	/**
	 * Returns the default values of all columns of a table. Supported for Oracle, HSQL, PostgreSQL and vendors
	 * with an information_schema supporting SCHEMA() (MySQL, MariaDB). PostgreSQL integer defaults are returned as Integer.
	 *
	 * @param connection database connection
	 * @param tableName name of the table, optionally with schema prefix for PostgreSQL
	 * @return default values by lowercased column names (null values for columns without default value)
	 * @throws Exception if a parameter is empty or the default values cannot be read
	 */
	public static CaseInsensitiveMap<Object> getColumnDefaultValues(final Connection connection, final String tableName) throws Exception {
		if (connection == null) {
			throw new Exception("Invalid empty connection for getColumnDefaultValue");
		} else if (Utilities.isBlank(tableName)) {
			throw new Exception("Invalid empty tableName for getColumnDefaultValues");
		} else {
			final CaseInsensitiveMap<Object> returnMap = new CaseInsensitiveMap<>();
			final DbVendor dbVendor = getDbVendor(connection);
			if (DbVendor.Oracle == dbVendor) {
				final String sql = "SELECT column_name, data_default FROM user_tab_cols WHERE table_name = ?";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName.toUpperCase());
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							final String columnName = resultSet.getString(1);
							final String defaultvalue = resultSet.getString(2);
							if (defaultvalue == null || "null".equalsIgnoreCase(defaultvalue)) {
								returnMap.put(columnName.toLowerCase(), null);
							} else if (defaultvalue.startsWith("'") && defaultvalue.endsWith("'")) {
								returnMap.put(columnName.toLowerCase(), defaultvalue.substring(1, defaultvalue.length() - 1));
							} else {
								returnMap.put(columnName.toLowerCase(), defaultvalue);
							}
						}
					}
				}
			} else if (DbVendor.HSQL == dbVendor) {
				final String sql = "SELECT column_name, column_default FROM information_schema.columns WHERE table_name = ?";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName);
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							final String columnName = resultSet.getString(1);
							String defaultvalue = resultSet.getString(2);
							if ("NULL".equalsIgnoreCase(defaultvalue)) {
								defaultvalue = null;
							}
							returnMap.put(columnName.toLowerCase(), defaultvalue);
						}
					}
				}
			} else if (DbVendor.PostgreSQL == dbVendor) {
				String schemaName;
				String tableNameWithoutSchema;
				if (tableName.contains(".")) {
					schemaName = tableName.substring(0, tableName.lastIndexOf("."));
					tableNameWithoutSchema = tableName.substring(tableName.lastIndexOf(".") + 1);
				} else {
					schemaName = null;
					tableNameWithoutSchema = tableName;
				}

				final String sql = "SELECT column_name, column_default FROM information_schema.columns WHERE " + (schemaName != null ? "LOWER(table_schema) = LOWER(?) AND " : "") + "table_name = ?";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					if (schemaName == null) {
						preparedStatement.setString(1, tableNameWithoutSchema);
					} else {
						preparedStatement.setString(1, schemaName);
						preparedStatement.setString(2, tableNameWithoutSchema);
					}
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							final String columnName = resultSet.getString(1);
							String defaultvalue = resultSet.getString(2);
							if (defaultvalue != null && defaultvalue.toLowerCase().endsWith("::integer")) {
								defaultvalue = defaultvalue.substring(0, defaultvalue.toLowerCase().indexOf("::integer"));
								if (defaultvalue.trim().startsWith("'") && defaultvalue.trim().endsWith("'")) {
									defaultvalue = defaultvalue.trim().substring(1, defaultvalue.trim().length() - 1);
								}
								returnMap.put(columnName.toLowerCase(), Integer.parseInt(defaultvalue));
							} else if ("NULL".equalsIgnoreCase(defaultvalue)) {
								defaultvalue = null;
								returnMap.put(columnName.toLowerCase(), defaultvalue);
							} else {
								returnMap.put(columnName.toLowerCase(), defaultvalue);
							}
						}
					}
				}
			} else {
				final String sql = "SELECT column_name, column_default FROM information_schema.columns WHERE table_schema = (SELECT schema()) AND table_name = ?";
				try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
					preparedStatement.setFetchSize(100);
					preparedStatement.setNString(1, tableName);
					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						while (resultSet.next()) {
							final String columnName = resultSet.getString(1);
							String defaultvalue = resultSet.getString(2);
							if ("NULL".equalsIgnoreCase(defaultvalue)) {
								defaultvalue = null;
							}
							returnMap.put(columnName.toLowerCase(), defaultvalue);
						}
					}
				}
			}
			return returnMap;
		}
	}

	/**
	 * Returns the SQL expression for a date default value. Supported for Oracle, MySQL and MariaDB.
	 *
	 * @param dataSource data source
	 * @param fieldDefault "sysdate", "sysdate()", "current_timestamp" or a date in the format "dd.MM.yyyy" (Oracle)
	 * @return SQL expression of the default value
	 * @throws Exception if the vendor is not supported
	 */
	public static String getDateDefaultValue(final DataSource dataSource, final String fieldDefault) throws Exception {
		final DbVendor dbVendor = getDbVendor(dataSource);
		if ("sysdate".equalsIgnoreCase(fieldDefault) || "sysdate()".equalsIgnoreCase(fieldDefault) || "current_timestamp".equalsIgnoreCase(fieldDefault)) {
			if (dbVendor == DbVendor.Oracle) {
				return "current_timestamp";
			} else if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB) {
				return "current_timestamp";
			} else {
				throw new Exception("Unsupported database vendor");
			}
		} else {
			if (dbVendor == DbVendor.Oracle) {
				return "to_date('" + fieldDefault + "', 'DD.MM.YYYY')";
			} else if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB) {
				return "'" + fieldDefault + "'";
			} else {
				throw new Exception("Unsupported database vendor");
			}
		}
	}

	/**
	 * Adds a column to a table, if it does not exist yet.
	 *
	 * @param dataSource data source
	 * @param tablename name of the table
	 * @param fieldname name of the new column
	 * @param fieldType type of the new column
	 * @param length length for VARCHAR types (100 if 0 or less)
	 * @param fieldDefault optional default value
	 * @param notNull true to make the column NOT NULL
	 * @return true if the column was added, false if a parameter is invalid, the column already exists or the statement failed
	 * @throws Exception if the existing columns or the vendor cannot be read
	 */
	public static boolean addColumnToDbTable(final DataSource dataSource, final String tablename, final String fieldname, String fieldType, int length, final String fieldDefault, final boolean notNull) throws Exception {
		if (Utilities.isBlank(fieldname)) {
			return false;
		} else if (!tablename.equalsIgnoreCase(getSQLSafeString(tablename))) {
			return false;
		} else if (Utilities.isBlank(fieldname)) {
			return false;
		} else if (!fieldname.equalsIgnoreCase(getSQLSafeString(fieldname))) {
			return false;
		} else if (Utilities.isBlank(fieldType)) {
			return false;
		} else if (containsColumnName(dataSource, tablename, fieldname)) {
			return false;
		} else {
			fieldType = fieldType.toUpperCase().trim();

			String addColumnStatement = "ALTER TABLE " + tablename + " ADD (" + fieldname.toLowerCase() + " " + fieldType;
			if (fieldType.startsWith("VARCHAR")) {
				if (length <= 0) {
					length = 100;
				}
				addColumnStatement += "(" + length + ")";
			}

			// Default Value
			if (Utilities.isNotEmpty(fieldDefault)) {
				if (fieldType.startsWith("VARCHAR")) {
					addColumnStatement += " DEFAULT '" + fieldDefault.replace("'", "''") + "'";
				} else if ("DATE".equalsIgnoreCase(fieldType)) {
					addColumnStatement += " DEFAULT " + getDateDefaultValue(dataSource, fieldDefault);
				} else {
					addColumnStatement += " DEFAULT " + fieldDefault;
				}
			}

			// Maybe null
			if (notNull) {
				addColumnStatement += " NOT NULL";
			}

			addColumnStatement += ")";

			try (Connection connection = dataSource.getConnection();
					Statement statement = connection.createStatement()) {
				statement.executeUpdate(addColumnStatement);
				return true;
			} catch (@SuppressWarnings("unused") final Exception e) {
				return false;
			}
		}
	}

	/**
	 * Changes the type of an existing column. Supported for Oracle, MySQL and MariaDB.
	 *
	 * @param dataSource data source
	 * @param tablename name of the table
	 * @param fieldname name of the column
	 * @param fieldType new type of the column
	 * @param length length for VARCHAR types (100 if 0 or less)
	 * @param fieldDefault optional default value
	 * @param notNull true to make the column NOT NULL
	 * @return true if the column was changed, false if a parameter is invalid, the column does not exist or the statement failed
	 * @throws Exception if the vendor is not supported or the existing columns cannot be read
	 */
	public static boolean alterColumnTypeInDbTable(final DataSource dataSource, final String tablename, final String fieldname, String fieldType, int length, final String fieldDefault, final boolean notNull) throws Exception {
		if (Utilities.isBlank(fieldname)) {
			return false;
		} else if (!tablename.equalsIgnoreCase(getSQLSafeString(tablename))) {
			return false;
		} else if (Utilities.isBlank(fieldname)) {
			return false;
		} else if (!fieldname.equalsIgnoreCase(getSQLSafeString(fieldname))) {
			return false;
		} else if (Utilities.isBlank(fieldType)) {
			return false;
		} else if (!containsColumnName(dataSource, tablename, fieldname)) {
			return false;
		} else {
			fieldType = fieldType.toUpperCase().trim();

			String changeColumnStatementPart = fieldname.toLowerCase() + " " + fieldType;
			if (fieldType.startsWith("VARCHAR")) {
				if (length <= 0) {
					length = 100;
				}
				changeColumnStatementPart += "(" + length + ")";
			}

			// Default Value
			if (Utilities.isNotEmpty(fieldDefault)) {
				if (fieldType.startsWith("VARCHAR")) {
					changeColumnStatementPart += " DEFAULT '" + fieldDefault.replace("'", "''") + "'";
				} else if ("DATE".equalsIgnoreCase(fieldType)) {
					changeColumnStatementPart += " DEFAULT " + getDateDefaultValue(dataSource, fieldDefault);
				} else {
					changeColumnStatementPart += " DEFAULT " + fieldDefault;
				}
			}

			// Maybe null
			if (notNull) {
				changeColumnStatementPart += " NOT NULL";
			}

			String changeColumnStatement;
			final DbVendor dbVendor = getDbVendor(dataSource);
			if (DbVendor.Oracle == dbVendor) {
				changeColumnStatement = "ALTER TABLE " + tablename + " MODIFY (" + changeColumnStatementPart + ")";
			} else if (DbVendor.MySQL == dbVendor || DbVendor.MariaDB == dbVendor) {
				changeColumnStatement = "ALTER TABLE " + tablename + " MODIFY " + changeColumnStatementPart;
			} else {
				throw new Exception("Unsupported database vendor");
			}

			try (Connection connection = dataSource.getConnection();
					Statement statement = connection.createStatement()) {
				statement.executeUpdate(changeColumnStatement);
				return true;
			} catch (@SuppressWarnings("unused") final Exception e) {
				return false;
			}
		}
	}

	private static String getSQLSafeString(final String value) {
		if (value == null) {
			return null;
		} else {
			return value.replace("'", "''");
		}
	}

	/**
	 * Checks if an Oracle tablespace exists (case-insensitive).
	 *
	 * @param dataSource data source
	 * @param tablespaceName name of the tablespace
	 * @return true if the tablespace exists, false if not or if the database is not an Oracle database or the name is null
	 * @throws Exception if the tablespaces cannot be read
	 */
	public static boolean checkOracleTablespaceExists(final DataSource dataSource, final String tablespaceName) throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			return checkOracleTablespaceExists(connection, tablespaceName);
		} catch (final SQLException e) {
			throw new Exception("Cannot check database tablespace " + tablespaceName + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Checks if an Oracle tablespace exists (case-insensitive).
	 *
	 * @param connection database connection
	 * @param tablespaceName name of the tablespace
	 * @return true if the tablespace exists, false if not or if the database is not an Oracle database or the name is null
	 * @throws Exception if the tablespaces cannot be read
	 */
	public static boolean checkOracleTablespaceExists(final Connection connection, final String tablespaceName) throws Exception {
		final DbVendor dbVendor = getDbVendor(connection);
		if (dbVendor == DbVendor.Oracle && tablespaceName != null) {
			try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM dba_tablespaces WHERE LOWER(tablespace_name) = ?")) {
				preparedStatement.setFetchSize(100);
				preparedStatement.setNString(1, tablespaceName.toLowerCase());
				try (ResultSet resultSet = preparedStatement.executeQuery()) {
					return resultSet.next() && resultSet.getInt(1) > 0;
				}
			} catch (final Exception e) {
				throw new Exception("Cannot check database tablespace " + tablespaceName + ": " + e.getMessage(), e);
			}
		} else {
			return false;
		}
	}

	/**
	 * Returns the primary key columns of a table.
	 *
	 * @param dataSource data source
	 * @param tableName name of the table
	 * @return primary key column names, empty if the table has no primary key, null if the table name is blank
	 * @throws Exception if the primary key cannot be read
	 */
	public static CaseInsensitiveSet getPrimaryKeyColumns(final DataSource dataSource, final String tableName) throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			return getPrimaryKeyColumns(connection, tableName);
		} catch (final SQLException e) {
			throw new Exception("Cannot read primarykey columns for table " + tableName + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Returns the primary key columns of a table (the partition key columns for Cassandra).
	 *
	 * @param connection database connection
	 * @param tableName name of the table, optionally with schema or keyspace prefix
	 * @return primary key column names, empty if the table has no primary key, null if the table name is blank
	 * @throws Exception if the primary key cannot be read
	 */
	public static CaseInsensitiveSet getPrimaryKeyColumns(final Connection connection, String tableName) throws Exception {
		if (Utilities.isBlank(tableName)) {
			return null;
		} else {
			final DbVendor dbVendor = getDbVendor(connection);
			try {
				if (DbVendor.Cassandra == dbVendor) {
					if (tableName.contains(".")) {
						final String keySpaceName = tableName.substring(0, tableName.indexOf("."));
						final String tableNameInKeySpace = tableName.substring(tableName.indexOf(".") + 1);
						final String sql = "SELECT column_name FROM system_schema.columns WHERE keyspace_name = ? AND table_name = ? AND kind = 'partition_key'";
						try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
							preparedStatement.setFetchSize(100);
							preparedStatement.setNString(1, keySpaceName);
							preparedStatement.setNString(2, tableNameInKeySpace);
							final CaseInsensitiveSet returnList = new CaseInsensitiveSet();
							try (ResultSet resultSet = preparedStatement.executeQuery() ) {
								while (resultSet.next()) {
									returnList.add(resultSet.getString("column_name"));
								}
							}
							return returnList;
						}
					} else {
						final String sql = "SELECT keyspace_name, column_name FROM system_schema.columns WHERE table_name = ? AND kind = 'partition_key' ALLOW FILTERING";
						try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
							preparedStatement.setFetchSize(100);
							preparedStatement.setNString(1, tableName);
							try (ResultSet resultSet = preparedStatement.executeQuery() ) {
								String keyspace = null;
								final CaseInsensitiveSet returnList = new CaseInsensitiveSet();
								while (resultSet.next()) {
									final String nextKeyspace = resultSet.getString("keyspace_name");
									if (keyspace == null) {
										keyspace = nextKeyspace;
									} else if (!keyspace.equals(nextKeyspace)) {
										throw new Exception("Multiple tables for table name '" + tableName + "' found. Please specify keyspace by keyspace_name prefix.");
									}
									returnList.add(resultSet.getString("column_name"));
								}
								return returnList;
							}
						}
					}
				} else {
					if (dbVendor == DbVendor.Oracle || dbVendor == DbVendor.HSQL || dbVendor == DbVendor.Derby) {
						tableName = tableName.toUpperCase();
					}

					String schemaName;
					String tableNameWithoutSchema;
					if (tableName.contains(".")) {
						schemaName = tableName.substring(0, tableName.lastIndexOf("."));
						tableNameWithoutSchema = tableName.substring(tableName.lastIndexOf(".") + 1);
					} else {
						schemaName = null;
						tableNameWithoutSchema = tableName;
					}

					final DatabaseMetaData metaData = connection.getMetaData();
					try (ResultSet resultSet = metaData.getPrimaryKeys(connection.getCatalog(), schemaName, tableNameWithoutSchema)) {
						final CaseInsensitiveSet returnList = new CaseInsensitiveSet();
						while (resultSet.next()) {
							returnList.add(resultSet.getString("COLUMN_NAME"));
						}
						return returnList;
					}
				}
			} catch (final Exception e) {
				throw new Exception("Cannot read primarykey columns for table " + tableName + ": " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Returns the names of all tables matching the given pattern expression.
	 *
	 * @param connection database connection
	 * @param tablePatternExpression list of table name patterns, separated by comma, blank, semicolon, pipe or line break.
	 *            Patterns may contain the wildcards {@code *} and {@code ?}. A leading {@code !} excludes the matching tables.
	 * @return names of the matching tables
	 * @throws Exception if the tables cannot be read
	 */
	public static List<String> getAvailableTables(final Connection connection, final String tablePatternExpression) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.setFetchSize(100);
			final DbVendor dbVendor = getDbVendor(connection);

			String tableQuery;
			if (DbVendor.Oracle == dbVendor) {
				tableQuery = "SELECT DISTINCT table_name FROM all_tables WHERE owner NOT IN ('CTXSYS', 'DBSNMP', 'MDDATA', 'MDSYS', 'DMSYS', 'OLAPSYS', 'ORDPLUGINS', 'OUTLN', 'SI_INFORMATN_SCHEMA', 'SYS', 'SYSMAN', 'SYSTEM')";
				for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
					if (Utilities.isNotBlank(tablePattern)) {
						tablePattern = tablePattern.trim().toUpperCase().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						if (tablePattern.startsWith("!")) {
							tableQuery += " AND table_name NOT LIKE '" + tablePattern.substring(1) + "' ESCAPE '\\'";
						} else {
							tableQuery += " AND table_name LIKE '" + tablePattern + "' ESCAPE '\\'";
						}
					}
				}
				tableQuery += " ORDER BY table_name";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						tableNamesToExport.add(resultSet.getString("table_name"));
					}
					return tableNamesToExport;
				}
			} else if (DbVendor.MySQL == dbVendor) {
				final List<String> inclusionPatterns = new ArrayList<>();
				final List<String> exclusionPatterns = new ArrayList<>();
				for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
					if (Utilities.isNotBlank(tablePattern)) {
						boolean exlusionPattern = false;
						if (tablePattern.startsWith("!")) {
							tablePattern = tablePattern.substring(1);
							exlusionPattern = true;
						}
						String schemaName;
						String tableNamePattern;
						if (tablePattern.contains(".")) {
							schemaName = tablePattern.substring(0, tablePattern.lastIndexOf("."));
							tableNamePattern = tablePattern.substring(tablePattern.lastIndexOf(".") + 1).trim().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						} else {
							schemaName = null;
							tableNamePattern = tablePattern.trim().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						}

						if (exlusionPattern) {
							if (schemaName == null) {
								exclusionPatterns.add("NOT (table_schema = SCHEMA() AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')");
							} else {
								exclusionPatterns.add("NOT (table_schema = '" + schemaName + "' AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')");
							}
						} else {
							if (schemaName == null) {
								inclusionPatterns.add("(table_schema = SCHEMA() AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')");
							} else {
								inclusionPatterns.add("(table_schema = '" + schemaName + "' AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')");
							}
						}
					}
				}
				tableQuery = "SELECT DISTINCT table_schema, table_name FROM information_schema.tables";
				if (inclusionPatterns.size() > 0) {
					tableQuery += " WHERE (" + Utilities.join(inclusionPatterns, " OR ") + ")";
					if (exclusionPatterns.size() > 0) {
						tableQuery += " AND " + Utilities.join(exclusionPatterns, " AND ");
					}
				} else if (exclusionPatterns.size() > 0) {
					tableQuery += " WHERE " + Utilities.join(exclusionPatterns, " AND ");
				}
				tableQuery += " ORDER BY table_schema, table_name";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					statement.setFetchSize(100);
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						if (Utilities.isNotBlank(resultSet.getString("table_schema"))) {
							tableNamesToExport.add(resultSet.getString("table_schema") + "." + resultSet.getString("table_name"));
						} else {
							tableNamesToExport.add(resultSet.getString("table_name"));
						}
					}
					return tableNamesToExport;
				}
			} else if (DbVendor.MariaDB == dbVendor) {
				final List<String> inclusionPatterns = new ArrayList<>();
				final List<String> exclusionPatterns = new ArrayList<>();
				for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
					if (Utilities.isNotBlank(tablePattern)) {
						boolean exlusionPattern = false;
						if (tablePattern.startsWith("!")) {
							tablePattern = tablePattern.substring(1);
							exlusionPattern = true;
						}
						String schemaName;
						String tableNamePattern;
						if (tablePattern.contains(".")) {
							schemaName = tablePattern.substring(0, tablePattern.lastIndexOf("."));
							tableNamePattern = tablePattern.substring(tablePattern.lastIndexOf(".") + 1).trim().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						} else {
							schemaName = null;
							tableNamePattern = tablePattern.trim().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						}

						if (exlusionPattern) {
							if (schemaName == null) {
								exclusionPatterns.add("NOT (table_schema = SCHEMA() AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')");
							} else {
								exclusionPatterns.add("NOT (table_schema = '" + schemaName + "' AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')");
							}
						} else {
							if (schemaName == null) {
								inclusionPatterns.add("(table_schema = SCHEMA() AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')");
							} else {
								inclusionPatterns.add("(table_schema = '" + schemaName + "' AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')");
							}
						}
					}
				}
				tableQuery = "SELECT DISTINCT table_schema, table_name FROM information_schema.tables";
				if (inclusionPatterns.size() > 0) {
					tableQuery += " WHERE (" + Utilities.join(inclusionPatterns, " OR ") + ")";
					if (exclusionPatterns.size() > 0) {
						tableQuery += " AND " + Utilities.join(exclusionPatterns, " AND ");
					}
				} else if (exclusionPatterns.size() > 0) {
					tableQuery += " WHERE " + Utilities.join(exclusionPatterns, " AND ");
				}
				tableQuery += " ORDER BY table_schema, table_name";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					statement.setFetchSize(100);
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						if (Utilities.isNotBlank(resultSet.getString("table_schema"))) {
							tableNamesToExport.add(resultSet.getString("table_schema") + "." + resultSet.getString("table_name"));
						} else {
							tableNamesToExport.add(resultSet.getString("table_name"));
						}
					}
					return tableNamesToExport;
				}
			} else if (DbVendor.PostgreSQL == dbVendor) {
				tableQuery = "SELECT DISTINCT table_schema, table_name FROM information_schema.tables WHERE table_schema NOT IN ('information_schema', 'pg_catalog')";
				final List<String> inclusionPatterns = new ArrayList<>();
				for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
					if (Utilities.isNotBlank(tablePattern)) {
						boolean exlusionPattern = false;
						if (tablePattern.startsWith("!")) {
							tablePattern = tablePattern.substring(1);
							exlusionPattern = true;
						}
						String schemaName;
						String tableNamePattern;
						if (tablePattern.contains(".")) {
							schemaName = tablePattern.substring(0, tablePattern.lastIndexOf("."));
							tableNamePattern = tablePattern.substring(tablePattern.lastIndexOf(".") + 1).trim().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						} else {
							schemaName = null;
							tableNamePattern = tablePattern.trim().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						}

						if (exlusionPattern) {
							if (schemaName == null) {
								tableQuery += " AND table_name NOT LIKE '" + tableNamePattern + "' ESCAPE '\\'";
							} else {
								tableQuery += " AND NOT (table_schema = '" + schemaName + "' AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')";
							}
						} else {
							if (schemaName == null) {
								inclusionPatterns.add("table_name LIKE '" + tableNamePattern + "' ESCAPE '\\'");
							} else {
								inclusionPatterns.add("(table_schema = '" + schemaName + "' AND table_name LIKE '" + tableNamePattern + "' ESCAPE '\\')");
							}
						}
					}
				}
				if (inclusionPatterns.size() > 0) {
					tableQuery += " AND (" + Utilities.join(inclusionPatterns, " OR ") + ")";
				}
				tableQuery += " ORDER BY table_schema, table_name";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					statement.setFetchSize(100);
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						if (Utilities.isNotBlank(resultSet.getString("table_schema")) && !"public".equalsIgnoreCase(resultSet.getString("table_schema"))) {
							tableNamesToExport.add(resultSet.getString("table_schema") + "." + resultSet.getString("table_name"));
						} else {
							tableNamesToExport.add(resultSet.getString("table_name"));
						}
					}
					return tableNamesToExport;
				}
			} else if (DbVendor.SQLite == dbVendor) {
				tableQuery = "SELECT name FROM sqlite_master WHERE type = 'table'";
				for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
					if (Utilities.isNotBlank(tablePattern)) {
						tablePattern = tablePattern.trim().toUpperCase().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						if (tablePattern.startsWith("!")) {
							tableQuery += " AND name NOT LIKE '" + tablePattern.substring(1) + "' ESCAPE '\\'";
						} else {
							tableQuery += " AND name LIKE '" + tablePattern + "' ESCAPE '\\'";
						}
					}
				}
				tableQuery += " ORDER BY name";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						tableNamesToExport.add(resultSet.getString("name"));
					}
					return tableNamesToExport;
				}
			} else if (DbVendor.Derby == dbVendor) {
				tableQuery = "SELECT tablename FROM sys.systables WHERE tabletype = 'T'";
				for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
					if (Utilities.isNotBlank(tablePattern)) {
						tablePattern = tablePattern.trim().toUpperCase().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						if (tablePattern.startsWith("!")) {
							tableQuery += " AND tablename NOT LIKE '" + tablePattern.substring(1) + "' {ESCAPE '\\'}";
						} else {
							tableQuery += " AND tablename LIKE '" + tablePattern + "' {ESCAPE '\\'}";
						}
					}
				}
				tableQuery += " ORDER BY tablename";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						tableNamesToExport.add(resultSet.getString("tablename"));
					}
					return tableNamesToExport;
				}
			} else if (DbVendor.Firebird == dbVendor) {
				tableQuery = "SELECT TRIM(rdb$relation_name) AS table_name FROM rdb$relations WHERE rdb$view_blr IS NULL AND (rdb$system_flag IS NULL OR rdb$system_flag = 0)";
				for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
					if (Utilities.isNotBlank(tablePattern)) {
						tablePattern = tablePattern.trim().toUpperCase().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						if (tablePattern.startsWith("!")) {
							tableQuery += " AND TRIM(rdb$relation_name) NOT LIKE '" + tablePattern.substring(1) + "' ESCAPE '\\'";
						} else {
							tableQuery += " AND TRIM(rdb$relation_name) LIKE '" + tablePattern + "' ESCAPE '\\'";
						}
					}
				}
				tableQuery += " ORDER BY rdb$relation_name";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						tableNamesToExport.add(resultSet.getString("table_name"));
					}
					return tableNamesToExport;
				}
			} else if (DbVendor.HSQL == dbVendor) {
				tableQuery = "SELECT table_name FROM information_schema.system_tables WHERE table_type = 'TABLE' AND table_schem = 'PUBLIC'";
				for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
					if (Utilities.isNotBlank(tablePattern)) {
						tablePattern = tablePattern.trim().toUpperCase().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						if (tablePattern.startsWith("!")) {
							tableQuery += " AND table_name NOT LIKE '" + tablePattern.substring(1) + "' ESCAPE '\\'";
						} else {
							tableQuery += " AND table_name LIKE '" + tablePattern + "' ESCAPE '\\'";
						}
					}
				}
				tableQuery += " ORDER BY table_name";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						tableNamesToExport.add(resultSet.getString("table_name"));
					}
					return tableNamesToExport;
				}
			} else if (DbVendor.Cassandra == dbVendor) {
				final List<String> systemKeySpaces = Arrays.asList(new String[] {"system", "system_auth", "system_schema", "system_distributed", "system_traces"});
				tableQuery = "SELECT keyspace_name, table_name FROM system_schema.tables";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						final String keySpaceName = resultSet.getString("keyspace_name");
						if (!systemKeySpaces.contains(keySpaceName)) {
							final String tableName = resultSet.getString("table_name");
							// Same semantics as the LIKE patterns of the other vendors: the whole table name must match,
							// a leading "!" excludes matching tables
							boolean hasInclusionPattern = false;
							boolean included = false;
							boolean excluded = false;
							for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
								if (Utilities.isNotBlank(tablePattern)) {
									tablePattern = tablePattern.trim();
									final boolean isExclusionPattern = tablePattern.startsWith("!");
									if (isExclusionPattern) {
										tablePattern = tablePattern.substring(1);
									} else {
										hasInclusionPattern = true;
									}
									final StringBuilder regex = new StringBuilder();
									for (final char patternChar : tablePattern.toCharArray()) {
										if (patternChar == '*' || patternChar == '%') {
											regex.append(".*");
										} else if (patternChar == '?' || patternChar == '_') {
											regex.append(".");
										} else {
											regex.append(Pattern.quote(Character.toString(patternChar)));
										}
									}
									if (Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(tableName).matches()) {
										if (isExclusionPattern) {
											excluded = true;
										} else {
											included = true;
										}
									}
								}
							}
							final boolean addTable = !excluded && (included || !hasInclusionPattern);

							if (addTable) {
								tableNamesToExport.add(tableName);
							}
						}
					}
					return tableNamesToExport;
				}
			} else if (DbVendor.MsSQL == dbVendor) {
				tableQuery = "SELECT DISTINCT table_name FROM information_schema.tables";
				boolean firstCondition = true;
				for (String tablePattern : tablePatternExpression.split(",| |;|\\||\n")) {
					if (Utilities.isNotBlank(tablePattern)) {
						tablePattern = tablePattern.trim().replace("%", "\\%").replace("_", "\\_").replace("*", "%").replace("?", "_");
						final String conjunction = firstCondition ? " WHERE " : " AND ";
						firstCondition = false;
						if (tablePattern.startsWith("!")) {
							tableQuery += conjunction + "table_name NOT LIKE '" + tablePattern.substring(1) + "' ESCAPE '\\'";
						} else {
							tableQuery += conjunction + "table_name LIKE '" + tablePattern + "' ESCAPE '\\'";
						}
					}
				}
				tableQuery += " ORDER BY table_name";

				try (ResultSet resultSet = statement.executeQuery(tableQuery)) {
					statement.setFetchSize(100);
					final List<String> tableNamesToExport = new ArrayList<>();
					while (resultSet.next()) {
						tableNamesToExport.add(resultSet.getString("table_name"));
					}
					return tableNamesToExport;
				}
			} else {
				throw new Exception("Unknown database vendor");
			}
		}
	}

	/**
	 * Creates a new table using vendor specific data types for the simple data types of the columns.
	 *
	 * @param connection database connection
	 * @param tablename name of the new table
	 * @param columnsAndTypes column names and types (a null type creates a VARCHAR(1) column)
	 * @param keyColumns optional primary key columns, must be contained in columnsAndTypes
	 * @throws Exception if a key column is missing in the columns or the table cannot be created
	 */
	public static void createTable(final Connection connection, final String tablename, final Map<String, DbColumnType> columnsAndTypes, final Collection<String> keyColumns) throws Exception {
		if (keyColumns != null) {
			for (String keyColumn : keyColumns) {
				keyColumn = Utilities.trimSimultaneously(Utilities.trimSimultaneously(keyColumn, "\""), "`");
				if (!columnsAndTypes.containsKey(keyColumn)) {
					throw new Exception("Cannot create table. Keycolumn '" + keyColumn + "' is not included in column types");
				}
			}
		}

		try (Statement statement = connection.createStatement()) {
			final DbVendor dbVendor = getDbVendor(connection);

			String columnPart = "";
			for (final Entry<String, DbColumnType> columnAndType : columnsAndTypes.entrySet()) {
				if (columnPart.length() > 0) {
					columnPart += ", ";
				}
				final DbColumnType columnType = columnAndType.getValue();
				if (columnType != null) {
					final String dataType = getDataType(dbVendor, columnType.getSimpleDataType());
					int dataLength = 0;
					if (dbVendor != DbVendor.Cassandra) {
						if (dataType.toLowerCase().contains("varchar")) {
							dataLength = (int) columnType.getCharacterByteSize();
							if (dataLength <= 0) {
								dataLength = 1;
							}
						}
					}
					columnPart += escapeVendorReservedNames(dbVendor, columnAndType.getKey()) + " " + dataType + (dataLength > 0 ? "(" + dataLength + ")" : "");
				} else {
					final String dataType = getDataType(dbVendor, DbSimpleDataType.String);
					columnPart += escapeVendorReservedNames(dbVendor, columnAndType.getKey()) + " " + dataType + "(1)";
				}
			}

			String primaryKeyPart = "";
			if (Utilities.isNotEmpty(keyColumns)) {
				primaryKeyPart = ", PRIMARY KEY (" + joinColumnVendorEscaped(dbVendor, keyColumns) + ")";
			}
			statement.execute("CREATE TABLE " + tablename + " (" + columnPart + primaryKeyPart + ")");
			if (dbVendor == DbVendor.Derby) {
				connection.commit();
			}
		}
	}

	/**
	 * Returns the vendor specific data type for a simple data type.
	 *
	 * @param dbVendor database vendor
	 * @param simpleDataType simple data type
	 * @return vendor specific type name, e.g. "VARCHAR2" for Oracle strings
	 * @throws Exception if the vendor is not supported
	 */
	public static String getDataType(final DbVendor dbVendor, final DbSimpleDataType simpleDataType) throws Exception {
		if (dbVendor == DbVendor.Oracle) {
			switch (simpleDataType) {
				case Blob: return "BLOB";
				case Clob: return "CLOB";
				case DateTime: return "TIMESTAMP";
				case Date: return "DATE";
				case Integer: return "NUMBER";
				case BigInteger: return "NUMBER";
				case Float: return "NUMBER";
				case String: return "VARCHAR2";
				case Boolean: return "BOOLEAN";
				default: return "VARCHAR2";
			}
		} else if (dbVendor == DbVendor.MySQL) {
			switch (simpleDataType) {
				case Blob: return "LONGBLOB";
				case Clob: return "LONGTEXT";
				case DateTime: return "TIMESTAMP NULL";
				case Date: return "DATE";
				case Integer: return "INT";
				case BigInteger: return "BIGINT";
				case Float: return "DOUBLE";
				case String: return "VARCHAR";
				case Boolean: return "BOOLEAN";
				default: return "VARCHAR";
			}
		} else if (dbVendor == DbVendor.MariaDB) {
			switch (simpleDataType) {
				case Blob: return "LONGBLOB";
				case Clob: return "LONGTEXT";
				case DateTime: return "TIMESTAMP NULL";
				case Date: return "DATE";
				case Integer: return "INT";
				case BigInteger: return "BIGINT";
				case Float: return "DOUBLE";
				case String: return "VARCHAR";
				case Boolean: return "BOOLEAN";
				default: return "VARCHAR";
			}
		} else if (dbVendor == DbVendor.HSQL) {
			switch (simpleDataType) {
				case Blob: return "BLOB";
				case Clob: return "CLOB";
				case DateTime: return "TIMESTAMP";
				case Date: return "DATE";
				case Integer: return "INTEGER";
				case BigInteger: return "BIGINT";
				case Float: return "DOUBLE";
				case String: return "VARCHAR";
				case Boolean: return "BOOLEAN";
				default: return "VARCHAR";
			}
		} else if (dbVendor == DbVendor.PostgreSQL) {
			switch (simpleDataType) {
				case Blob: return "BYTEA";
				case Clob: return "TEXT";
				case DateTime: return "TIMESTAMP";
				case Date: return "DATE";
				case Integer: return "INTEGER";
				case BigInteger: return "BIGINT";
				case Float: return "REAL";
				case String: return "VARCHAR";
				case Boolean: return "BOOLEAN";
				default: return "VARCHAR";
			}
		} else if (dbVendor == DbVendor.SQLite) {
			switch (simpleDataType) {
				case Blob: return "BLOB";
				case Clob: return "CLOB";
				case DateTime: return "TIMESTAMP";
				case Date: return "DATE";
				case Integer: return "INTEGER";
				case BigInteger: return "INTEGER";
				case Float: return "DOUBLE";
				case String: return "VARCHAR";
				case Boolean: return "BOOLEAN";
				default: return "VARCHAR";
			}
		} else if (dbVendor == DbVendor.Derby) {
			switch (simpleDataType) {
				case Blob: return "BLOB";
				case Clob: return "CLOB";
				case DateTime: return "TIMESTAMP";
				case Date: return "DATE";
				case Integer: return "INTEGER";
				case BigInteger: return "BIGINT";
				case Float: return "DOUBLE";
				case String: return "VARCHAR";
				case Boolean: return "BOOLEAN";
				default: return "VARCHAR";
			}
		} else if (dbVendor == DbVendor.Firebird) {
			switch (simpleDataType) {
				case Blob: return "BLOB SUB_TYPE BINARY";
				case Clob: return "BLOB SUB_TYPE TEXT";
				case DateTime: return "TIMESTAMP";
				case Date: return "DATE";
				case Integer: return "INTEGER";
				case BigInteger: return "BIGINT";
				case Float: return "DOUBLE PRECISION";
				case String: return "VARCHAR";
				case Boolean: return "BOOLEAN";
				default: return "VARCHAR";
			}
		} else if (dbVendor == DbVendor.Cassandra) {
			switch (simpleDataType) {
				case Blob: return "BLOB";
				case Clob: return "TEXT";
				case DateTime: return "TIMESTAMP";
				case Date: return "DATE";
				case Integer: return "INT";
				case BigInteger: return "BIGINT";
				case Float: return "DOUBLE";
				case String: return "VARCHAR";
				case Boolean: return "BOOLEAN";
				default: return "VARCHAR";
			}
		} else if (dbVendor == DbVendor.MsSQL) {
			switch (simpleDataType) {
				case Blob: return "VARBINARY(MAX)";
				case Clob: return "VARCHAR(MAX)";
				case DateTime: return "DATETIME";
				case Date: return "DATE";
				case Integer: return "INT";
				case BigInteger: return "BIGINT";
				case Float: return "REAL";
				case String: return "VARCHAR";
				case Boolean: return "BIT";
				default: return "VARCHAR";
			}
		} else {
			throw new Exception("Cannot get datatype: " + dbVendor + "/" + simpleDataType);
		}
	}

	/**
	 * Returns the name of a {@link Types} constant.
	 *
	 * @param typeId value of a {@link Types} constant
	 * @return name of the type, e.g. "VARCHAR"
	 * @throws Exception if the type id is unknown
	 */
	public static String getTypeNameById(final int typeId) throws Exception {
		switch (typeId) {
			case Types.BIT: return "BIT";
			case Types.TINYINT: return "TINYINT";
			case Types.SMALLINT: return "SMALLINT";
			case Types.INTEGER: return "INTEGER";
			case Types.BIGINT: return "BIGINT";
			case Types.FLOAT: return "FLOAT";
			case Types.REAL: return "REAL";
			case Types.DOUBLE: return "DOUBLE";
			case Types.NUMERIC: return "NUMERIC";
			case Types.DECIMAL: return "DECIMAL";
			case Types.CHAR: return "CHAR";
			case Types.VARCHAR: return "VARCHAR";
			case Types.LONGVARCHAR: return "LONGVARCHAR";
			case Types.DATE: return "DATE";
			case Types.TIME: return "TIME";
			case Types.TIMESTAMP: return "TIMESTAMP";
			case Types.BINARY: return "BINARY";
			case Types.VARBINARY: return "VARBINARY";
			case Types.LONGVARBINARY: return "LONGVARBINARY";
			case Types.NULL: return "NULL";
			case Types.OTHER: return "OTHER";
			case Types.JAVA_OBJECT: return "JAVA_OBJECT";
			case Types.DISTINCT: return "DISTINCT";
			case Types.STRUCT: return "STRUCT";
			case Types.ARRAY: return "ARRAY";
			case Types.BLOB: return "BLOB";
			case Types.CLOB: return "CLOB";
			case Types.REF: return "REF";
			case Types.DATALINK: return "DATALINK";
			case Types.BOOLEAN: return "BOOLEAN";
			case Types.ROWID: return "ROWID";
			case Types.NCHAR: return "NCHAR";
			case Types.NVARCHAR: return "NVARCHAR";
			case Types.LONGNVARCHAR: return "LONGNVARCHAR";
			case Types.NCLOB: return "NCLOB";
			case Types.SQLXML: return "SQLXML";
			case Types.REF_CURSOR: return "REF_CURSOR";
			case Types.TIME_WITH_TIMEZONE: return "TIME_WITH_TIMEZONE";
			case Types.TIMESTAMP_WITH_TIMEZONE: return "TIMESTAMP_WITH_TIMEZONE";
			default: throw new Exception("Unknown type id: " + typeId);
		}
	}

	/**
	 * Returns the download location of the JDBC driver of a database vendor.
	 *
	 * @param dbVendor database vendor
	 * @return download url
	 * @throws Exception if there is no known download location for the vendor
	 */
	public static String getDownloadUrl(final DbVendor dbVendor) throws Exception {
		if (dbVendor == DbVendor.MySQL) {
			return DOWNLOAD_LOCATION_MYSQL;
		} else if (dbVendor == DbVendor.MariaDB) {
			return DOWNLOAD_LOCATION_MARIADB;
		} else if (dbVendor == DbVendor.Oracle) {
			return DOWNLOAD_LOCATION_ORACLE;
		} else if (dbVendor == DbVendor.PostgreSQL) {
			return DOWNLOAD_LOCATION_POSTGRESQL;
		} else if (dbVendor == DbVendor.Firebird) {
			return DOWNLOAD_LOCATION_FIREBIRD;
		} else if (dbVendor == DbVendor.Derby) {
			return DOWNLOAD_LOCATION_DERBY;
		} else if (dbVendor == DbVendor.SQLite) {
			return DOWNLOAD_LOCATION_SQLITE;
		} else if (dbVendor == DbVendor.HSQL) {
			return DOWNLOAD_LOCATION_HSQL;
		} else if (dbVendor == DbVendor.MsSQL) {
			return DOWNLOAD_LOCATION_MSSQL;
		} else {
			throw new Exception("Invalid database vendor");
		}
	}

	/**
	 * Sets the duplicateIndexColumn of all entries of a table to the minimum itemIndexColumn value of all entries
	 * with the same key values (duplicates). The changes are committed.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param keyColumnsWithFunctions key columns defining duplicates, optionally wrapped in a function like {@code LOWER(email)}
	 * @param itemIndexColumn column with the index of each entry
	 * @param duplicateIndexColumn column to store the index of the first duplicate in
	 * @throws Exception if no key columns are given or the update fails (the changes are rolled back then)
	 */
	public static void markDuplicates(final Connection connection, final String tableName, final Collection<String> keyColumnsWithFunctions, String itemIndexColumn, String duplicateIndexColumn) throws Exception {
		if (Utilities.isNotEmpty(keyColumnsWithFunctions)) {
			final DbVendor dbVendor = getDbVendor(connection);
			itemIndexColumn = escapeVendorReservedNames(dbVendor, itemIndexColumn);
			duplicateIndexColumn = escapeVendorReservedNames(dbVendor, duplicateIndexColumn);

			final StringBuilder selectPart = new StringBuilder();
			final StringBuilder wherePart = new StringBuilder();
			int columnIndex = 0;
			for (String columnWithFunction : keyColumnsWithFunctions) {
				columnWithFunction = escapeColumnNameOrExpression(dbVendor, columnWithFunction.trim());

				if (selectPart.length() > 0) {
					selectPart.append(", ");
					wherePart.append(" AND ");
				}

				selectPart.append(columnWithFunction + " AS col" + columnIndex);

				wherePart.append("subselect.col" + columnIndex);

				wherePart.append(" = ");

				if (columnWithFunction.contains("(")) {
					wherePart.append(columnWithFunction.replace("(", "(" + tableName + "."));
				} else {
					wherePart.append(tableName + "." + columnWithFunction);
				}

				columnIndex++;
			}

			try (Statement statement = connection.createStatement()) {
				// The indirection with a subselect is needed for MySQL (You can't specify target table for update in FROM clause)
				final String setDuplicateReferences = "UPDATE " + tableName + " SET " + duplicateIndexColumn + " = (SELECT subselect." + itemIndexColumn + " FROM"
						+ " (SELECT " + selectPart.toString() + ", MIN(" + itemIndexColumn + ") AS " + itemIndexColumn + " FROM " + tableName + " GROUP BY " + joinColumnVendorEscaped(dbVendor, keyColumnsWithFunctions) + ") subselect"
						+ " WHERE " + wherePart.toString() + ")";
				statement.executeUpdate(setDuplicateReferences);
				connection.commit();
			} catch (final Exception e) {
				connection.rollback();
				throw new Exception("Cannot markDuplicates: " + e.getMessage(), e);
			}
		} else {
			throw new Exception("Cannot markDuplicates: Missing keycolumns");
		}
	}

	/**
	 * Creates the condition for comparing the key columns of two tables, e.g. {@code a.id = b.id AND LOWER(a.email) = LOWER(b.email)}.
	 *
	 * @param dbVendor database vendor for escaping reserved names
	 * @param columnNamesWithFunctions key columns, optionally wrapped in a function like {@code LOWER(email)}
	 * @param tableAlias1 alias of the first table, may be blank
	 * @param tableAlias2 alias of the second table, may be blank
	 * @return equations combined by AND
	 */
	public static String getKeyColumnEquationList(final DbVendor dbVendor, final Collection<String> columnNamesWithFunctions, final String tableAlias1, final String tableAlias2) {
		final StringBuilder returnValue = new StringBuilder();
		for (String columnName : columnNamesWithFunctions) {
			columnName = escapeColumnNameOrExpression(dbVendor, columnName.trim());

			if (returnValue.length() > 0) {
				// The equations are used as conditions within WHERE clauses, so they must be combined by AND
				returnValue.append(" AND ");
			}

			if (Utilities.isNotBlank(tableAlias1)) {
				if (columnName.contains("(")) {
					returnValue.append(columnName.replace("(", "(" + tableAlias1 + "."));
				} else {
					returnValue.append(tableAlias1 + "." + columnName);
				}
			} else {
				returnValue.append(columnName);
			}

			returnValue.append(" = ");

			if (Utilities.isNotBlank(tableAlias2)) {
				if (columnName.contains("(")) {
					returnValue.append(columnName.replace("(", "(" + tableAlias2 + "."));
				} else {
					returnValue.append(tableAlias2 + "." + columnName);
				}
			} else {
				returnValue.append(columnName);
			}
		}
		return returnValue.toString();
	}

	/**
	 * Counts the key values occurring more than once in a table.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param keyColumnsWithFunctions key columns, optionally wrapped in a function like {@code LOWER(email)}
	 * @return number of duplicate key values (not the number of duplicate rows)
	 * @throws Exception if the duplicates cannot be counted
	 */
	public static int detectDuplicates(final Connection connection, final String tableName, final Collection<String> keyColumnsWithFunctions) throws Exception {
		try (Statement statement = connection.createStatement()) {
			final String countDuplicatesStatement = "SELECT COUNT(*) FROM (SELECT COUNT(*) FROM " + tableName + " GROUP BY " + joinColumnVendorEscaped(getDbVendor(connection), keyColumnsWithFunctions) + " HAVING COUNT(*) > 1) subsel";
			try (ResultSet resultSet = statement.executeQuery(countDuplicatesStatement)) {
				if (resultSet.next()) {
					return resultSet.getInt(1);
				} else {
					return 0;
				}
			}
		} catch (final Exception e) {
			throw new Exception("Cannot detectDuplicates: " + e.getMessage(), e);
		}
	}

	/**
	 * Counts the rows of a table whose key values also exist in another table.
	 *
	 * @param connection database connection
	 * @param detectTableName table whose rows are counted
	 * @param fromTableName table with the existing key values
	 * @param keyColumnsWithFunctions key columns, optionally wrapped in a function like {@code LOWER(email)}
	 * @return number of rows in detectTableName with key values existing in fromTableName
	 * @throws Exception if the rows cannot be counted
	 */
	public static int detectDuplicatesCrossTables(final Connection connection, final String detectTableName, final String fromTableName, final List<String> keyColumnsWithFunctions) throws Exception {
		try (Statement statement = connection.createStatement()) {
			final String selectDuplicatesNumber = "SELECT COUNT(*) FROM " + detectTableName + " a WHERE EXISTS (SELECT 1 FROM " + fromTableName + " b WHERE " + getKeyColumnEquationList(getDbVendor(connection), keyColumnsWithFunctions, "a", "b") + ")";
			try (ResultSet resultSet = statement.executeQuery(selectDuplicatesNumber)) {
				resultSet.next();
				return resultSet.getInt(1);
			}
		} catch (final Exception e) {
			throw new Exception("Cannot detectDuplicatesCrossTables: " + e.getMessage(), e);
		}
	}

	/**
	 * Adds a new BIGINT column with an index to a table. If the column name already exists,
	 * a suffix "_1" to "_9" is appended.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param columnBaseName base name of the new column
	 * @return name of the created column
	 * @throws Exception if no free column name was found or the column cannot be created
	 */
	public static String addIndexedIntegerColumn(final Connection connection, final String tableName, final String columnBaseName) throws Exception {
		final DbVendor dbVendor = getDbVendor(connection);

		String columnName = columnBaseName;
		int i = 0;
		while (checkTableAndColumnsExist(connection, tableName, columnName) && i < 10) {
			i++;
			columnName = columnBaseName + "_" + i;
		}
		if (i >= 10) {
			throw new Exception("Cannot create columnBaseName " + columnBaseName + " in table " + tableName);
		}

		try (Statement statement = connection.createStatement()) {
			statement.execute("ALTER TABLE " + tableName + " ADD " + columnName + " " + DbUtilities.getDataType(dbVendor, DbSimpleDataType.BigInteger));
			if (dbVendor != DbVendor.Cassandra) {
				try {
					statement.execute("CREATE INDEX tmp" + new Random().nextInt(100000000) + "_idx ON " + tableName + " (" + columnName + ")");
				} catch (final Exception e) {
					e.printStackTrace();
					// Work without index. Maybe it already exists or it is a not allowed function based index
				}
			}
		}

		return columnName;
	}

	/**
	 * Deletes the rows of a table whose key values also exist in another table. The changes are committed.
	 *
	 * @param connection database connection
	 * @param keepInTableName table with the rows to keep
	 * @param deleteInTableName table with the rows to delete
	 * @param keyColumnsWithFunctions key columns, optionally wrapped in a function like {@code LOWER(email)}
	 * @return number of deleted rows (0 if no key columns are given)
	 * @throws Exception if the rows cannot be deleted (the changes are rolled back then)
	 */
	public static int dropDuplicatesCrossTable(final Connection connection, final String keepInTableName, final String deleteInTableName, final List<String> keyColumnsWithFunctions) throws Exception {
		if (Utilities.isNotEmpty(keyColumnsWithFunctions)) {
			final DbVendor dbVendor = getDbVendor(connection);
			try (Statement statement = connection.createStatement()) {
				final String deleteDuplicates = "DELETE FROM " + deleteInTableName + " WHERE " + joinColumnVendorEscaped(dbVendor, keyColumnsWithFunctions) + " IN (SELECT " + joinColumnVendorEscaped(dbVendor, keyColumnsWithFunctions) + " FROM " + keepInTableName + ")";
				final int numberOfDeletedDuplicates = statement.executeUpdate(deleteDuplicates);
				connection.commit();
				return numberOfDeletedDuplicates;
			} catch (final Exception e) {
				connection.rollback();
				throw new Exception("Cannot deleteTableCrossDuplicates: " + e.getMessage(), e);
			}
		} else {
			return 0;
		}
	}

	/**
	 * Deletes all duplicates of a table, so only the first row of each key value remains. The changes are committed.
	 * Temporary helper columns are added and removed again.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param keyColumns key columns, optionally wrapped in a function like {@code LOWER(email)}
	 * @return number of deleted rows
	 * @throws Exception if the duplicates cannot be deleted (the changes are rolled back then)
	 */
	public static int dropDuplicates(final Connection connection, final String tableName, final Collection<String> keyColumns) throws Exception {
		if (detectDuplicates(connection, tableName, keyColumns) > 0) {
			final DbVendor dbVendor = getDbVendor(connection);

			String originalItemIndexColumn = null;
			String originalDuplicateIndexColumn = null;
			try (Statement statement = connection.createStatement()) {
				originalItemIndexColumn = createLineNumberIndexColumn(connection, tableName, "drop_idx");
				originalDuplicateIndexColumn = addIndexedIntegerColumn(connection, tableName, "drop_dpl");

				// Try to create an additional column index
				if (Utilities.isNotEmpty(keyColumns) && dbVendor != DbVendor.Cassandra) {
					try {
						statement.execute("CREATE INDEX tmp" + new Random().nextInt(100000000) + "_idx ON " + tableName + " (" + joinColumnVendorEscaped(getDbVendor(connection), keyColumns) + ")");
					} catch (@SuppressWarnings("unused") final Exception e) {
						// Work without index. Maybe it already exists or it is a not allowed function based index
					}
				}

				markDuplicates(connection, tableName, keyColumns, originalItemIndexColumn, originalDuplicateIndexColumn);

				final int numberOfDeletedDuplicates = statement.executeUpdate("DELETE FROM " + tableName + " WHERE " + originalItemIndexColumn + " != " + originalDuplicateIndexColumn);
				connection.commit();
				return numberOfDeletedDuplicates;
			} catch (final Exception e) {
				connection.rollback();
				throw new Exception("Cannot dropDuplicates: " + e.getMessage(), e);
			} finally {
				dropColumnIfExists(connection, tableName, originalItemIndexColumn);
				dropColumnIfExists(connection, tableName, originalDuplicateIndexColumn);
			}
		} else {
			return 0;
		}
	}

	/**
	 * Joins all duplicates of a table into the first row of each key value: The duplicates are deleted and their
	 * values are used to update the remaining row. Temporary helper columns and an interim table are used.
	 * <p>
	 * Not supported for Cassandra, which cannot fill the interim table.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param keyColumnsWithFunctions key columns, optionally wrapped in a function like {@code LOWER(email)}
	 * @param updateWithNullValues true to also take over NULL values of the duplicates
	 * @return number of deleted duplicate rows
	 * @throws Exception if the duplicates cannot be joined
	 */
	public static int joinDuplicates(final Connection connection, final String tableName, final Collection<String> keyColumnsWithFunctions, final boolean updateWithNullValues) throws Exception {
		if (detectDuplicates(connection, tableName, keyColumnsWithFunctions) > 0) {
			final DbVendor dbVendor = getDbVendor(connection);
			final String randomSuffix = "" + new Random().nextInt(100000000);
			final String interimTableName = "tmp_join_" + randomSuffix;
			String originalItemIndexColumn = null;
			String originalDuplicateIndexColumn = null;

			// Join all duplicates in destination table
			try (Statement statement = connection.createStatement()) {
				// Create additional index columns
				if (Utilities.isNotEmpty(keyColumnsWithFunctions) && dbVendor != DbVendor.Cassandra) {
					try {
						statement.execute("CREATE INDEX tmp" + randomSuffix + "_idx ON " + tableName + " (" + joinColumnVendorEscaped(dbVendor, keyColumnsWithFunctions) + ")");
						connection.commit();
					} catch (@SuppressWarnings("unused") final Exception e) {
						// Work without index. Maybe it already exists or it is a not allowed function based index
					}
				}

				originalItemIndexColumn = createLineNumberIndexColumn(connection, tableName, "join_idx");
				originalDuplicateIndexColumn = addIndexedIntegerColumn(connection, tableName, "join_dpl");

				markDuplicates(connection, tableName, keyColumnsWithFunctions, originalItemIndexColumn, originalDuplicateIndexColumn);
				connection.commit();

				// Create temp table
				if (dbVendor == DbVendor.HSQL || dbVendor == DbVendor.Derby) {
					statement.execute("CREATE TABLE " + interimTableName + " AS (SELECT * FROM " + tableName + ") WITH NO DATA");
					statement.executeUpdate("INSERT INTO " + interimTableName + " (SELECT * FROM " + tableName + " WHERE " + originalItemIndexColumn + " != " + originalDuplicateIndexColumn + ")");
				} else if (dbVendor == DbVendor.PostgreSQL) {
					// Close a maybe open transaction to allow DDL-statement
					connection.rollback();
					statement.execute("CREATE TABLE " + interimTableName + " AS SELECT * FROM " + tableName + " WHERE " + originalItemIndexColumn + " != " + originalDuplicateIndexColumn);
				} else if (dbVendor == DbVendor.Firebird) {
					// There is no "create table as select"-statement in firebird, so the table must be created and filled separately
					createTable(connection, interimTableName, getColumnDataTypes(connection, tableName), null);
					connection.commit();
					statement.executeUpdate("INSERT INTO " + interimTableName + " SELECT * FROM " + tableName + " WHERE " + originalItemIndexColumn + " != " + originalDuplicateIndexColumn);
				} else if (dbVendor == DbVendor.Cassandra) {
					// There is no "create table as select"-statement in Cassandra
					// TODO: Cassandra also has no "INSERT INTO ... SELECT", so the interim table stays empty and the duplicate data is not joined
					createTable(connection, interimTableName, getColumnDataTypes(connection, tableName), getPrimaryKeyColumns(connection, tableName));
				} else {
					statement.execute("CREATE TABLE " + interimTableName + " AS SELECT * FROM " + tableName + " WHERE " + originalItemIndexColumn + " != " + originalDuplicateIndexColumn);
				}
				connection.commit();

				final int deletedDuplicatesInDB = dropDuplicates(connection, tableName, keyColumnsWithFunctions);

				final List<String> columnsWithoutAutoIncrement = new ArrayList<>();
				for (final Entry<String, DbColumnType> column : getColumnDataTypes(connection, tableName).entrySet()) {
					if (!column.getValue().isAutoIncrement()) {
						columnsWithoutAutoIncrement.add(column.getKey());
					}
				}

				updateAllExistingItems(connection, interimTableName, tableName, columnsWithoutAutoIncrement, keyColumnsWithFunctions, originalItemIndexColumn, updateWithNullValues, null);
				connection.commit();
				return deletedDuplicatesInDB;
			} catch (final Exception e) {
				throw new Exception("Cannot joinDuplicates: " + e.getMessage(), e);
			} finally {
				dropTableIfExists(connection, interimTableName);
				dropColumnIfExists(connection, tableName, originalItemIndexColumn);
				dropColumnIfExists(connection, tableName, originalDuplicateIndexColumn);
			}
		} else {
			return 0;
		}
	}

	private static String createLineNumberIndexColumn(final Connection connection, final String tableName, final String indexColumnNameBaseName) throws Exception {
		final DbVendor dbVendor = getDbVendor(connection);
		final String indexColumnName = addIndexedIntegerColumn(connection, tableName, indexColumnNameBaseName);

		try (Statement statement = connection.createStatement()) {
			if (dbVendor == DbVendor.MySQL) {
				statement.execute("SELECT @n := 0");
				statement.execute("UPDATE " + tableName + " SET " + indexColumnName + " = @n := @n + 1");
			} else if (dbVendor == DbVendor.MariaDB) {
				statement.execute("SELECT @n := 0");
				statement.execute("UPDATE " + tableName + " SET " + indexColumnName + " = @n := @n + 1");
			} else if (dbVendor == DbVendor.Oracle) {
				statement.executeUpdate("UPDATE " + tableName + " SET " + indexColumnName + " = ROWNUM");
			} else if (dbVendor == DbVendor.SQLite) {
				statement.executeUpdate("UPDATE " + tableName + " SET " + indexColumnName + " = ROWID");
			} else if (dbVendor == DbVendor.HSQL) {
				statement.executeUpdate("UPDATE " + tableName + " SET " + indexColumnName + " = ROWNUM()");
			} else if (dbVendor == DbVendor.Derby) {
				String autoIncrementColumn = null;
				final List<String> columnsWithoutAutoIncrement = new ArrayList<>();
				for (final Entry<String, DbColumnType> column : getColumnDataTypes(connection, tableName).entrySet()) {
					if (!column.getValue().isAutoIncrement()) {
						columnsWithoutAutoIncrement.add(column.getKey());
					} else {
						autoIncrementColumn = column.getKey();
					}
				}
				columnsWithoutAutoIncrement.remove(indexColumnName);
				if (autoIncrementColumn != null) {
					statement.executeUpdate("UPDATE " + tableName + " SET " + indexColumnName + " = " + autoIncrementColumn);
				} else {
					statement.executeUpdate("INSERT INTO " + tableName + " (" + joinColumnVendorEscaped(dbVendor, columnsWithoutAutoIncrement) + ", " + indexColumnName + ") (SELECT " + joinColumnVendorEscaped(dbVendor, columnsWithoutAutoIncrement) + ", ROW_NUMBER() OVER() FROM " + tableName + ")");
					statement.executeUpdate("DELETE FROM " + tableName + " WHERE " + indexColumnName + " IS NULL");
				}
			} else if (dbVendor == DbVendor.PostgreSQL) {
				String autoIncrementColumn = null;
				final List<String> columnsWithoutAutoIncrement = new ArrayList<>();
				for (final Entry<String, DbColumnType> column : getColumnDataTypes(connection, tableName).entrySet()) {
					if (!column.getValue().isAutoIncrement()) {
						columnsWithoutAutoIncrement.add(column.getKey());
					} else {
						autoIncrementColumn = column.getKey();
					}
				}
				columnsWithoutAutoIncrement.remove(indexColumnName);
				if (autoIncrementColumn != null) {
					statement.executeUpdate("UPDATE " + tableName + " SET " + indexColumnName + " = " + autoIncrementColumn);
				} else {
					statement.executeUpdate("INSERT INTO " + tableName + " (" + joinColumnVendorEscaped(dbVendor, columnsWithoutAutoIncrement) + ", " + indexColumnName + ") (SELECT " + joinColumnVendorEscaped(dbVendor, columnsWithoutAutoIncrement) + ", ROW_NUMBER() OVER() FROM " + tableName + ")");
					statement.executeUpdate("DELETE FROM " + tableName + " WHERE " + indexColumnName + " IS NULL");
				}
			} else if (dbVendor == DbVendor.Cassandra) {
				statement.execute("UPDATE " + tableName + " SET " + indexColumnName + " = " + getPrimaryKeyColumns(connection, tableName).iterator().next());
			} else if (dbVendor == DbVendor.Firebird) {
				String autoIncrementColumn = null;
				final List<String> columnsWithoutAutoIncrement = new ArrayList<>();
				for (final Entry<String, DbColumnType> column : getColumnDataTypes(connection, tableName).entrySet()) {
					if (!column.getValue().isAutoIncrement()) {
						columnsWithoutAutoIncrement.add(column.getKey());
					} else {
						autoIncrementColumn = column.getKey();
					}
				}
				columnsWithoutAutoIncrement.remove(indexColumnName);
				if (autoIncrementColumn != null) {
					statement.executeUpdate("UPDATE " + tableName + " SET " + indexColumnName + " = " + autoIncrementColumn);
				} else {
					statement.executeUpdate("INSERT INTO " + tableName + " (" + joinColumnVendorEscaped(dbVendor, columnsWithoutAutoIncrement) + ", " + indexColumnName + ") (SELECT " + joinColumnVendorEscaped(dbVendor, columnsWithoutAutoIncrement) + ", ROW_NUMBER() OVER() FROM " + tableName + ")");
					statement.executeUpdate("DELETE FROM " + tableName + " WHERE " + indexColumnName + " IS NULL");
				}
			} else if (dbVendor == DbVendor.MsSQL) {
				String autoIncrementColumn = null;
				final List<String> columnsWithoutAutoIncrement = new ArrayList<>();
				for (final Entry<String, DbColumnType> column : getColumnDataTypes(connection, tableName).entrySet()) {
					if (!column.getValue().isAutoIncrement()) {
						columnsWithoutAutoIncrement.add(column.getKey());
					} else {
						autoIncrementColumn = column.getKey();
					}
				}
				columnsWithoutAutoIncrement.remove(indexColumnName);
				if (autoIncrementColumn != null) {
					statement.executeUpdate("UPDATE " + tableName + " SET " + indexColumnName + " = " + autoIncrementColumn);
				} else {
					// MsSQL requires an explicit ORDER BY within OVER(), unlike PostgreSQL/Derby/Firebird
					statement.executeUpdate("INSERT INTO " + tableName + " (" + joinColumnVendorEscaped(dbVendor, columnsWithoutAutoIncrement) + ", " + indexColumnName + ") (SELECT " + joinColumnVendorEscaped(dbVendor, columnsWithoutAutoIncrement) + ", ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) FROM " + tableName + ")");
					statement.executeUpdate("DELETE FROM " + tableName + " WHERE " + indexColumnName + " IS NULL");
				}
			} else {
				throw new Exception("Unsupported database vendor");
			}
			connection.commit();
		} catch (final Exception e) {
			throw new Exception("Cannot create lineNumberIndexColumn: " + e.getMessage(), e);
		}
		return indexColumnName;
	}

	/**
	 * Drops a column, if it exists. The change is committed.
	 * For SQLite versions without "ALTER TABLE ... DROP COLUMN" (before 3.35.0) the table is recreated without the column,
	 * which only works for simple column definitions.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param columnName name of the column
	 * @return true if the column was dropped, false if a parameter is null or the column does not exist
	 * @throws Exception if the column cannot be dropped (the changes are rolled back then)
	 */
	public static boolean dropColumnIfExists(final Connection connection, final String tableName, final String columnName) throws Exception {
		if (connection != null && tableName != null && columnName != null && checkTableAndColumnsExist(connection, tableName, columnName)) {
			final DbVendor dbVendor = getDbVendor(connection);
			if (dbVendor == DbVendor.SQLite) {
				// SQLite supports "ALTER TABLE ... DROP COLUMN" since version 3.35.0
				try (Statement statement = connection.createStatement()) {
					statement.execute("ALTER TABLE " + tableName + " DROP COLUMN " + escapeVendorReservedNames(dbVendor, columnName));
					connection.commit();
					return true;
				} catch (@SuppressWarnings("unused") final SQLException e) {
					// Older SQLite versions cannot drop columns, so the table is recreated without the column
				}

				try (Statement statement = connection.createStatement()) {
					final String randomSuffix = "" + new Random().nextInt(100000000);

					String createTableStatement = null;
					try (ResultSet resultSet = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND LOWER(name) = LOWER('" + tableName + "')")) {
						if (resultSet.next()) {
							createTableStatement = resultSet.getString("sql");
						} else {
							throw new Exception("Cannot find create table sql");
						}
					}

					final String originalCreateTableStatement = createTableStatement;
					createTableStatement = createTableStatement.replaceAll(", " + Pattern.quote(columnName) + " [a-zA-Z0-9]+(?:\\([0-9]+(?:, ?[0-9]+)?\\))?\\)", ")").replaceAll(", " + Pattern.quote(columnName) + " [a-zA-Z0-9]+(?:\\([0-9]+(?:, ?[0-9]+)?\\))?,", ",");
					if (createTableStatement.equals(originalCreateTableStatement)) {
						// The simple column definition pattern did not match (e.g. first column or additional column constraints), so the column would not be dropped
						throw new Exception("Cannot drop column '" + columnName + "' of SQLite table '" + tableName + "': Unsupported column definition");
					}

					statement.execute("ALTER TABLE " + tableName + " RENAME TO tmp" + randomSuffix + "_old");
					statement.execute(createTableStatement);
					final Set<String> columns = getColumnNames(connection, tableName);
					columns.remove(columnName);
					statement.execute("INSERT INTO " + tableName + " (" + joinColumnVendorEscaped(dbVendor, columns) + ") SELECT " + joinColumnVendorEscaped(dbVendor, columns) + " FROM tmp" + randomSuffix + "_old");
					statement.execute("DROP TABLE tmp" + randomSuffix + "_old");
					connection.commit();
					return true;
				} catch (final Exception e) {
					connection.rollback();
					throw e;
				}
			} else {
				try (Statement statement = connection.createStatement()) {
					statement.execute("ALTER TABLE " + tableName + " DROP " + (dbVendor == DbVendor.Cassandra ? "" : "COLUMN ") + columnName);
					connection.commit();
					return true;
				} catch (final Exception e) {
					connection.rollback();
					throw e;
				}
			}
		}
		return false;
	}

	/**
	 * Drops a table, if it exists. The change is committed.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @return true if the table was dropped, false if a parameter is null or the table does not exist
	 * @throws Exception if the table cannot be dropped
	 */
	public static boolean dropTableIfExists(final Connection connection, final String tableName) throws Exception {
		if (connection != null && tableName != null && checkTableExist(connection, tableName)) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("DROP TABLE " + tableName);
				connection.commit();
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a new empty table with the structure of the given columns of another table.
	 * All columns except the key columns are made nullable.
	 *
	 * @param connection database connection
	 * @param sourceTableName table to copy the structure from
	 * @param columnNames columns to copy (all columns for Firebird and Cassandra)
	 * @param keyColumns key columns, which keep their NOT NULL constraint, may be null
	 * @param destinationTableName name of the new table
	 * @throws Exception if the table cannot be created
	 */
	public static void copyTableStructure(final Connection connection, final String sourceTableName, final List<String> columnNames, final List<String> keyColumns, final String destinationTableName) throws Exception {
		try (Statement statement = connection.createStatement()) {
			final DbVendor dbVendor = getDbVendor(connection);
			if (dbVendor == DbVendor.HSQL || dbVendor == DbVendor.Derby) {
				statement.execute("CREATE TABLE " + destinationTableName + " AS (SELECT " + joinColumnVendorEscaped(dbVendor, columnNames) + " FROM " + sourceTableName + ") WITH NO DATA");
			} else if (dbVendor == DbVendor.PostgreSQL) {
				// Close a maybe open transaction to allow DDL-statement
				connection.rollback();
				statement.execute("CREATE TABLE " + destinationTableName + " AS SELECT " + joinColumnVendorEscaped(dbVendor, columnNames) + " FROM " + sourceTableName + " WHERE 1 = 0");
			} else if (dbVendor == DbVendor.Firebird) {
				// There is no "create table as select"-statement in firebird
				createTable(connection, destinationTableName, getColumnDataTypes(connection, sourceTableName), null);
			} else if (dbVendor == DbVendor.Cassandra) {
				// There is no "create table as select"-statement in Cassandra
				createTable(connection, destinationTableName, getColumnDataTypes(connection, sourceTableName), getPrimaryKeyColumns(connection, sourceTableName));
			} else {
				statement.execute("CREATE TABLE " + destinationTableName + " AS SELECT " + joinColumnVendorEscaped(dbVendor, columnNames) + " FROM " + sourceTableName + " WHERE 1 = 0");
			}

			if (dbVendor != DbVendor.Cassandra) {
				// Make all columns nullable
				final CaseInsensitiveMap<DbColumnType> columnDataTypes = getColumnDataTypes(connection, destinationTableName);
				for (final Entry<String, DbColumnType> columnDataType : columnDataTypes.entrySet()) {
					// The column names of getColumnDataTypes() are lowercased, so the key columns must be compared case-insensitive
					if (!columnDataType.getValue().isNullable() && (keyColumns == null || !new CaseInsensitiveSet(keyColumns).contains(columnDataType.getKey()))) {
						String typeString = columnDataType.getValue().getTypeName();
						if (columnDataType.getValue().getSimpleDataType() == DbSimpleDataType.String) {
							typeString += "(" + (Long.toString(columnDataType.getValue().getCharacterByteSize())) + ")";
						} else if (columnDataType.getValue().getSimpleDataType() == DbSimpleDataType.Float) {
							typeString += "(" + (Integer.toString(columnDataType.getValue().getNumericPrecision())) + ")";
						}

						if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB || dbVendor == DbVendor.Oracle) {
							statement.execute("ALTER TABLE " + destinationTableName + " MODIFY " + columnDataType.getKey() + " " + typeString + " NULL");
						} else if (dbVendor == DbVendor.PostgreSQL) {
							statement.execute("ALTER TABLE " + destinationTableName + " ALTER COLUMN " + columnDataType.getKey() + " DROP NOT NULL");
						} else if (dbVendor == DbVendor.MsSQL) {
							statement.execute("ALTER TABLE " + destinationTableName + " ALTER COLUMN " + columnDataType.getKey() + " " + typeString + " NULL");
						} else if (dbVendor == DbVendor.HSQL || dbVendor == DbVendor.Derby) {
							statement.execute("ALTER TABLE " + destinationTableName + " ALTER COLUMN " + columnDataType.getKey() + " NULL");
						} else if (dbVendor == DbVendor.SQLite) {
							// SQLite's CREATE TABLE ... AS SELECT never carries over NOT NULL constraints from the source table,
							// so this should be unreachable for SQLite. SQLite also has no ALTER COLUMN/MODIFY statement at all,
							// so if this is ever reached, fail clearly instead of throwing a cryptic SQL syntax error.
							throw new Exception("Cannot make column '" + columnDataType.getKey() + "' nullable in SQLite table '" + destinationTableName + "': SQLite does not support altering column nullability");
						} else {
							statement.execute("ALTER TABLE " + destinationTableName + " MODIFY " + columnDataType.getKey() + " " + typeString + " NULL");
						}
					}
				}
			}

			if (dbVendor == DbVendor.PostgreSQL || dbVendor == DbVendor.Firebird) {
				connection.commit();
			}
		}
	}

	/**
	 * Creates an index with a generated name (at most 30 characters) on a table.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param columns indexed columns, optionally wrapped in a function like {@code LOWER(email)}
	 * @return name of the created index
	 * @throws Exception if the index cannot be created
	 */
	public static String createIndex(final Connection connection, final String tableName, final List<String> columns) throws Exception {
		try (Statement statement = connection.createStatement()) {
			final String indexNameSuffix = "_" + new Random().nextInt(100000000) + "_ix";
			final String indexName = Utilities.shortenStringToMaxLengthCutRight(tableName, 30 - indexNameSuffix.length(), "") + indexNameSuffix;
			statement.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + joinColumnVendorEscaped(getDbVendor(connection), columns) + ")");
			return indexName;
		} catch (final Exception e) {
			throw new Exception("Cannot create index: " + e.getMessage(), e);
		}
	}

	/**
	 * Deletes all rows of a table (without commit).
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @return number of deleted rows
	 * @throws Exception if the rows cannot be deleted
	 */
	public static int clearTable(final Connection connection, final String tableName) throws Exception {
		try (Statement statement = connection.createStatement()) {
			return statement.executeUpdate("DELETE FROM " + tableName);
		} catch (final Exception e) {
			throw new Exception("Cannot clear table: " + e.getMessage(), e);
		}
	}

	/**
	 * Copies all rows of a source table, whose key values do not exist in the destination table yet. The changes are committed.
	 *
	 * @param connection database connection
	 * @param sourceTableName table to copy the rows from
	 * @param destinationTableName table to insert the rows into
	 * @param insertColumns columns to copy
	 * @param keyColumnsWithFunctions key columns, optionally wrapped in a function like {@code LOWER(email)}; if empty, all rows are copied
	 * @param additionalInsertValues optional additional values as lines "column = SQL value", separated by line breaks or semicolons
	 * @return number of inserted rows
	 * @throws Exception if the rows cannot be inserted (the changes are rolled back then)
	 */
	public static int insertNotExistingItems(final Connection connection, final String sourceTableName, final String destinationTableName, final List<String> insertColumns, final List<String> keyColumnsWithFunctions, final String additionalInsertValues) throws Exception {
		final DbVendor dbVendor = getDbVendor(connection);
		try (Statement statement = connection.createStatement()) {
			String additionalInsertValuesSqlColumns = "";
			String additionalInsertValuesSqlValues = "";
			if (Utilities.isNotBlank(additionalInsertValues)) {
				for (final String line : Utilities.splitAndTrimListQuoted(additionalInsertValues, '\n', '\r', ';')) {
					final String columnName = line.substring(0, line.indexOf("=")).trim();
					final String columnvalue = line.substring(line.indexOf("=") + 1).trim();
					additionalInsertValuesSqlColumns += columnName + ", ";
					additionalInsertValuesSqlValues += columnvalue + ", ";
				}
			}

			String insertDataStatement = "INSERT INTO " + destinationTableName + " (" + additionalInsertValuesSqlColumns + joinColumnVendorEscaped(dbVendor, insertColumns) + ") SELECT " + additionalInsertValuesSqlValues + joinColumnVendorEscaped(dbVendor, insertColumns) + " FROM " + sourceTableName + " a";
			if (Utilities.isNotEmpty(keyColumnsWithFunctions)) {
				insertDataStatement += " WHERE NOT EXISTS (SELECT 1 FROM " + destinationTableName + " b WHERE " + getKeyColumnEquationList(dbVendor, keyColumnsWithFunctions, "a", "b") + ")";
			}
			final int numberOfInserts = statement.executeUpdate(insertDataStatement);
			connection.commit();
			return numberOfInserts;
		} catch (final Exception e) {
			connection.rollback();
			throw new Exception("Cannot insert: " + e.getMessage(), e);
		}
	}

	/**
	 * Deletes all rows of a temporary table, which would violate a NOT NULL constraint (without default value)
	 * of the destination table. Empty strings are treated like NULL values.
	 *
	 * @param connection database connection
	 * @param tempTableName temporary table with the new rows
	 * @param destinationTableName table the rows will be inserted into
	 * @param columnsToInsert columns which will be inserted, null for all columns
	 * @param duplicateInDestinationTableColumn optional column of the temporary table, which marks rows already existing
	 *        in the destination table (value other than 0 or NULL); these rows are not deleted
	 * @return number of deleted rows
	 * @throws Exception if the rows cannot be deleted
	 */
	public static int removeNewEntriesWithInvalidNullValues(final Connection connection, final String tempTableName, final String destinationTableName, List<String> columnsToInsert, final String duplicateInDestinationTableColumn) throws Exception {
		if (columnsToInsert != null) {
			columnsToInsert = columnsToInsert.stream().map(x -> x.toLowerCase()).collect(Collectors.toList());
		}

		String notNullableDestinationColumnsPart = "";
		for (final Entry<String, DbColumnType> entry : DbUtilities.getColumnDataTypes(connection, destinationTableName).entrySet()) {
			if (!entry.getValue().isNullable() && (columnsToInsert == null || columnsToInsert.contains(entry.getKey().toLowerCase()))) {
				final String defaultValue = DbUtilities.getColumnDefaultValue(connection, destinationTableName, entry.getKey());
				if (Utilities.isEmpty(defaultValue)) {
					if (notNullableDestinationColumnsPart.length() > 0) {
						notNullableDestinationColumnsPart += " OR ";
					}
					if (entry.getValue().getSimpleDataType() == DbSimpleDataType.String) {
						notNullableDestinationColumnsPart += "(" + entry.getKey() + " IS NULL OR " + entry.getKey() + " = '')";
					} else {
						notNullableDestinationColumnsPart += entry.getKey() + " IS NULL";
					}
				}
			}
		}

		int removedItems = 0;
		if (notNullableDestinationColumnsPart.length() > 0) {
			try (Statement statement = connection.createStatement()) {
				if (duplicateInDestinationTableColumn != null) {
					removedItems = statement.executeUpdate("DELETE FROM " + tempTableName + " WHERE (" + duplicateInDestinationTableColumn + " = 0 OR " + duplicateInDestinationTableColumn + " IS NULL) AND (" + notNullableDestinationColumnsPart + ")");
				} else {
					removedItems = statement.executeUpdate("DELETE FROM " + tempTableName + " WHERE " + notNullableDestinationColumnsPart);
				}
			}
		}
		return removedItems;
	}

	/**
	 * Copies all rows of a source table into a destination table. The changes are committed.
	 *
	 * @param connection database connection
	 * @param sourceTableName table to copy the rows from
	 * @param destinationTableName table to insert the rows into
	 * @param insertColumns columns to copy
	 * @param additionalInsertValues optional additional values as lines "column = SQL value", separated by line breaks or semicolons
	 * @return number of inserted rows
	 * @throws Exception if the rows cannot be inserted (the changes are rolled back then)
	 */
	public static int insertAllItems(final Connection connection, final String sourceTableName, final String destinationTableName, final List<String> insertColumns, final String additionalInsertValues) throws Exception {
		return insertNotExistingItems(connection, sourceTableName, destinationTableName, insertColumns, null, additionalInsertValues);
	}

	/**
	 * Updates all rows of a destination table with the values of the rows with the same key values in a source table.
	 * If there are several matching source rows, the one with the highest item index is used. The changes are committed.
	 *
	 * @param connection database connection
	 * @param sourceTableName table with the new values
	 * @param destinationTableName table to update
	 * @param updateColumns columns to update (key columns are never updated)
	 * @param keyColumns key columns, optionally wrapped in a function like {@code LOWER(email)}
	 * @param itemIndexColumn column of the source table with the index of each row
	 * @param updateWithNullValues true to also take over NULL values, false to keep the existing values instead
	 * @param additionalUpdateValues optional additional values as lines "column = SQL value", separated by line breaks or semicolons
	 * @return number of updated rows
	 * @throws Exception if the key columns are missing or the rows cannot be updated (the changes are rolled back then)
	 */
	public static int updateAllExistingItems(final Connection connection, final String sourceTableName, final String destinationTableName, Collection<String> updateColumns, final Collection<String> keyColumns, String itemIndexColumn, final boolean updateWithNullValues, final String additionalUpdateValues) throws Exception {
		if (keyColumns == null || keyColumns.isEmpty()) {
			throw new Exception("Missing keycolumns");
		}

		// Do not update the keycolumns
		updateColumns = new ArrayList<>(updateColumns);
		updateColumns.removeAll(keyColumns);

		int updatedItems = 0;

		if (!updateColumns.isEmpty()) {
			try (Statement statement = connection.createStatement()) {
				String additionalUpdateValuesSql = "";
				if (Utilities.isNotBlank(additionalUpdateValues)) {
					for (final String line : Utilities.splitAndTrimListQuoted(additionalUpdateValues, '\n', '\r', ';')) {
						final String columnName = line.substring(0, line.indexOf("=")).trim();
						final String columnvalue = line.substring(line.indexOf("=") + 1).trim();
						additionalUpdateValuesSql += columnName + " = " + columnvalue + ", ";
					}
				}

				updateColumns = new ArrayList<>(updateColumns);
				updateColumns.removeAll(keyColumns);
				if (updateColumns.size() > 0 || additionalUpdateValuesSql.length() > 0) {
					final DbVendor dbVendor = getDbVendor(connection);
					itemIndexColumn = escapeVendorReservedNames(dbVendor, itemIndexColumn);
					String updatedIndexColumn = null;
					try {
						if (updateWithNullValues) {
							String updateSetPart = "";
							for (String updateColumn : updateColumns) {
								updateColumn = escapeVendorReservedNames(dbVendor, updateColumn);
								if (updateSetPart.length() > 0) {
									updateSetPart += ", ";
								}
								updateSetPart += updateColumn + " = (SELECT " + updateColumn + " FROM " + sourceTableName + " WHERE " + itemIndexColumn + " ="
										+ " (SELECT MAX(" + itemIndexColumn + ") FROM " + sourceTableName + " c WHERE " + getKeyColumnEquationList(dbVendor, keyColumns, destinationTableName, "c") + "))";
							}
							final String updateAllAtOnce = "UPDATE " + destinationTableName + " SET " + additionalUpdateValuesSql + updateSetPart
									+ " WHERE EXISTS (SELECT 1 FROM " + sourceTableName + " b WHERE " + getKeyColumnEquationList(dbVendor, keyColumns, destinationTableName, "b") + ")";
							updatedItems = statement.executeUpdate(updateAllAtOnce);
						} else {
							updatedIndexColumn = addIndexedIntegerColumn(connection, destinationTableName, "updatedindex");
							for (String updateColumn : updateColumns) {
								updateColumn = escapeVendorReservedNames(dbVendor, updateColumn);
								final String updateSingleColumn = "UPDATE " + destinationTableName
										+ " SET " + additionalUpdateValuesSql + updatedIndexColumn + " = 1, " + updateColumn + " = (SELECT " + updateColumn + " FROM " + sourceTableName + " WHERE " + itemIndexColumn + " ="
										+ " (SELECT MAX(" + itemIndexColumn + ") FROM " + sourceTableName + " c WHERE " + updateColumn + " IS NOT NULL AND " + getKeyColumnEquationList(dbVendor, keyColumns, destinationTableName, "c") + "))"
										+ " WHERE EXISTS (SELECT 1 FROM " + sourceTableName + " b WHERE " + updateColumn + " IS NOT NULL AND " + getKeyColumnEquationList(dbVendor, keyColumns, destinationTableName, "b") + ")";
								statement.executeUpdate(updateSingleColumn);
							}

							try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + destinationTableName + " WHERE " + updatedIndexColumn + " = 1")) {
								resultSet.next();
								updatedItems = resultSet.getInt(1);
							}
						}
						connection.commit();
					} catch (final Exception e) {
						connection.rollback();
						throw e;
					} finally {
						dropColumnIfExists(connection, destinationTableName, updatedIndexColumn);
					}
				}
			} catch (final Exception e) {
				throw new Exception("Cannot update: " + e.getMessage(), e);
			}
		}

		return updatedItems;
	}

	/**
	 * Updates only the first row of each key value in a destination table with the values of the rows with the same
	 * key values in a source table. If there are several matching source rows, the one with the highest item index is used.
	 * The item index column of the source table is overwritten with the index of the matched destination row. The changes are committed.
	 *
	 * @param connection database connection
	 * @param sourceTableName table with the new values
	 * @param destinationTableName table to update
	 * @param updateColumns columns to update (key columns are never updated)
	 * @param keyColumns key columns
	 * @param itemIndexColumn column of the source table with the index of each row
	 * @param updateWithNullValues true to also take over NULL values, false to keep the existing values instead
	 * @param additionalUpdateValues optional additional values as lines "column = SQL value", separated by line breaks or semicolons
	 * @return number of updated rows
	 * @throws Exception if the key columns are missing or the rows cannot be updated (the changes are rolled back then)
	 */
	public static int updateFirstExistingItems(final Connection connection, final String sourceTableName, final String destinationTableName, Collection<String> updateColumns, final Collection<String> keyColumns, final String itemIndexColumn, final boolean updateWithNullValues, final String additionalUpdateValues) throws Exception {
		if (keyColumns == null || keyColumns.isEmpty()) {
			throw new Exception("Missing keycolumns");
		}

		// Do not update the keycolumns
		updateColumns = new ArrayList<>(updateColumns);
		updateColumns.removeAll(keyColumns);

		int updatedItems = 0;

		if (!updateColumns.isEmpty()) {
			try (Statement statement = connection.createStatement()) {
				String additionalUpdateValuesSql = "";
				if (Utilities.isNotBlank(additionalUpdateValues)) {
					for (final String line : Utilities.splitAndTrimListQuoted(additionalUpdateValues, '\n', '\r', ';')) {
						final String columnName = line.substring(0, line.indexOf("=")).trim();
						final String columnvalue = line.substring(line.indexOf("=") + 1).trim();
						additionalUpdateValuesSql += columnName + " = " + columnvalue + ", ";
					}
				}

				updateColumns = new ArrayList<>(updateColumns);
				updateColumns.removeAll(keyColumns);
				if (updateColumns.size() > 0 || additionalUpdateValuesSql.length() > 0) {
					String originalItemIndexColumn = null;
					String updatedIndexColumn = null;
					try {
						final DbVendor dbVendor = getDbVendor(connection);
						originalItemIndexColumn = createLineNumberIndexColumn(connection, destinationTableName, "itemindex");

						// Mark duplicates in temp table
						final List<String> keycolumnParts = new ArrayList<>();
						for (final String keyColumn : keyColumns) {
							keycolumnParts.add("src." + escapeVendorReservedNames(dbVendor, keyColumn) + " = " + sourceTableName + "." + escapeVendorReservedNames(dbVendor, keyColumn) + " AND src." + escapeVendorReservedNames(dbVendor, keyColumn) + " IS NOT NULL");
						}
						final String updateStatement = "UPDATE " + sourceTableName + " SET " + itemIndexColumn + " = COALESCE((SELECT MIN(src." + originalItemIndexColumn + ") FROM " + destinationTableName + " src WHERE " + Utilities.join(keycolumnParts, " AND ") + "), 0)";
						statement.executeUpdate(updateStatement);
						connection.commit();

						// Update with marked items
						if (updateWithNullValues) {
							String updateSetPart = "";
							for (String updateColumn : updateColumns) {
								updateColumn = escapeVendorReservedNames(dbVendor, updateColumn);
								if (updateSetPart.length() > 0) {
									updateSetPart += ", ";
								}
								updateSetPart += updateColumn + " = (SELECT " + updateColumn + " FROM " + sourceTableName + " WHERE " + itemIndexColumn + " ="
										+ " (SELECT MAX(" + itemIndexColumn + ") FROM " + sourceTableName + " c WHERE " + getKeyColumnEquationList(dbVendor, keyColumns, destinationTableName, "c") + "))";
							}
							final String updateAllAtOnce = "UPDATE " + destinationTableName + " SET " + additionalUpdateValuesSql + updateSetPart
									+ " WHERE EXISTS (SELECT 1 FROM " + sourceTableName + " b WHERE " + originalItemIndexColumn + " = b." + itemIndexColumn + ")";
							updatedItems = statement.executeUpdate(updateAllAtOnce);
						} else {
							updatedIndexColumn = addIndexedIntegerColumn(connection, destinationTableName, "updatedindex");
							for (String updateColumn : updateColumns) {
								updateColumn = escapeVendorReservedNames(dbVendor, updateColumn);
								final String updateSingleColumn = "UPDATE " + destinationTableName
										+ " SET " + additionalUpdateValuesSql + updatedIndexColumn + " = 1, " + updateColumn + " = (SELECT " + updateColumn + " FROM " + sourceTableName + " WHERE " + itemIndexColumn + " ="
										+ " (SELECT MAX(" + itemIndexColumn + ") FROM " + sourceTableName + " c WHERE " + updateColumn + " IS NOT NULL AND " + getKeyColumnEquationList(dbVendor, keyColumns, destinationTableName, "c") + "))"
										+ " WHERE EXISTS (SELECT 1 FROM " + sourceTableName + " b WHERE " + originalItemIndexColumn + " = b." + itemIndexColumn + ")";
								statement.executeUpdate(updateSingleColumn);
							}

							try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + destinationTableName + " WHERE " + updatedIndexColumn + " = 1")) {
								resultSet.next();
								updatedItems = resultSet.getInt(1);
							}
						}
						connection.commit();
					} catch (final Exception e) {
						connection.rollback();
						throw e;
					} finally {
						dropColumnIfExists(connection, destinationTableName, originalItemIndexColumn);
						dropColumnIfExists(connection, destinationTableName, updatedIndexColumn);
					}
				}
			} catch (final Exception e) {
				throw new Exception("Cannot update first entries: " + e.getMessage(), e);
			}
		}

		return updatedItems;
	}

	/**
	 * Checks if each of the given columns is part of an index of the table.
	 * Supported for Oracle, MySQL and MariaDB.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @param keyColumns columns to check
	 * @return true if all columns are indexed, false if not, null if the check is not available for this database vendor
	 * @throws Exception if the index information cannot be read
	 * @throws RuntimeException for Cassandra databases, which are not supported
	 */
	public static Boolean checkForIndex(final Connection connection, final String tableName, final List<String> keyColumns) throws Exception {
		final DbVendor dbVendor = getDbVendor(connection);
		if (dbVendor == DbVendor.Oracle) {
			// Dictionary views contain the plain names, so quoted names must be unescaped and not escaped
			try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM user_ind_columns WHERE LOWER(table_name) = ? AND LOWER(column_name) = ?")) {
				for (final String keyColumn : keyColumns) {
					statement.setNString(1, unescapeVendorReservedNames(dbVendor, tableName.trim()).toLowerCase());
					statement.setNString(2, unescapeVendorReservedNames(dbVendor, keyColumn.trim()).toLowerCase());
					try (ResultSet resultSet = statement.executeQuery()) {
						if (!resultSet.next() || resultSet.getInt(1) <= 0) {
							return false;
						}
					}
				}
				return true;
			}
		} else if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB) {
			// information_schema with bind variables instead of "SHOW INDEX FROM <tableName>", so the table name cannot inject SQL
			String schemaName = null;
			String plainTableName = tableName.trim();
			if (plainTableName.contains(".")) {
				schemaName = unescapeVendorReservedNames(dbVendor, plainTableName.substring(0, plainTableName.indexOf(".")));
				plainTableName = plainTableName.substring(plainTableName.indexOf(".") + 1);
			}
			plainTableName = unescapeVendorReservedNames(dbVendor, plainTableName);

			final String indexQuery = "SELECT COUNT(*) FROM information_schema.statistics"
					+ " WHERE table_schema = " + (schemaName == null ? "DATABASE()" : "?")
					+ " AND LOWER(table_name) = ? AND LOWER(column_name) = ?";
			try (PreparedStatement statement = connection.prepareStatement(indexQuery)) {
				for (final String keyColumn : keyColumns) {
					int parameterIndex = 1;
					if (schemaName != null) {
						statement.setString(parameterIndex++, schemaName);
					}
					statement.setString(parameterIndex++, plainTableName.toLowerCase());
					statement.setString(parameterIndex, unescapeVendorReservedNames(dbVendor, keyColumn.trim()).toLowerCase());
					try (ResultSet resultSet = statement.executeQuery()) {
						if (!resultSet.next() || resultSet.getInt(1) <= 0) {
							return false;
						}
					}
				}
				return true;
			}
		} else if (dbVendor == DbVendor.Cassandra) {
//			final List<String> indexedColumns = new ArrayList<>();
//			indexedColumns.addAll(getPrimaryKeyColumns(connection, tableName));
//
//			try (PreparedStatement statement = connection.prepareStatement("SELECT options FROM system_schema.indexes WHERE table_name = ? ALLOW FILTERING")) {
//				statement.setNString(1, tableName);
//				try (ResultSet resultSet = statement.executeQuery()) {
//					while (resultSet.next()) {
//						final String options = resultSet.getString("options");
//						indexedColumns.add((String) ((JsonObject) JsonReader.readJsonItemString(options)).getSimpleValue("target"));
//					}
//				}
//
//				for (final String keyColumn : keyColumns) {
//					if (!indexedColumns.contains(keyColumn)) {
//						return false;
//					}
//				}
//				return true;
//			}
			throw new RuntimeException("This method is not supported for Cassandra databases: checkForIndex");
		} else {
			return null;
		}
	}

	/**
	 * Removes vendor specific quotes from a name (backticks for MySQL and MariaDB, double quotes for Oracle and Derby).
	 *
	 * @param dbVendor database vendor
	 * @param value name, possibly quoted
	 * @return name without quotes
	 */
	public static String unescapeVendorReservedNames(final DbVendor dbVendor, final String value) {
		if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB) {
			return Utilities.trimSimultaneously(value, "`");
		} else if (dbVendor == DbVendor.Oracle) {
			return Utilities.trimSimultaneously(value, "\"");
		} else if (dbVendor == DbVendor.Derby) {
			return Utilities.trimSimultaneously(value, "\"");
		} else {
			return value;
		}
	}

	/**
	 * Quotes a name with vendor specific quotes, if it is a reserved word or no plain identifier
	 * (see {@link #SAFE_IDENTIFIER}). Oracle names are uppercased when quoted.
	 *
	 * @param dbVendor database vendor
	 * @param value name to quote
	 * @return quoted or unchanged name
	 */
	public static String escapeVendorReservedNames(final DbVendor dbVendor, final String value) {
		if (Utilities.isBlank(value)) {
			return value;
		} else {
			final boolean needsQuoteForSyntax = !DbUtilities.SAFE_IDENTIFIER.matcher(value).matches();

			if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB) {
				if (needsQuoteForSyntax || RESERVED_WORDS_MYSSQL_MARIADB.contains(value.toLowerCase())) {
					return "`" + value.replaceAll("`", "``") + "`";
				} else {
					return value;
				}
			} else if (dbVendor == DbVendor.PostgreSQL) {
				if (needsQuoteForSyntax || RESERVED_WORDS_POSTGRESQL.contains(value.toLowerCase())) {
					return "\"" + value.replace("\"", "\"\"") + "\"";
				} else {
					return value;
				}
			} else if (dbVendor == DbVendor.Oracle) {
				if (needsQuoteForSyntax || RESERVED_WORDS_ORACLE.contains(value.toLowerCase())) {
					return "\"" + value.toUpperCase().replace("\"", "\"\"") + "\"";
				} else {
					return value;
				}
			} else if (dbVendor == DbVendor.Derby) {
				if (needsQuoteForSyntax || RESERVED_WORDS_DERBY.contains(value.toLowerCase())) {
					return "\"" + value.replace("\"", "\"\"") + "\"";
				} else {
					return value;
				}
			} else {
				if (needsQuoteForSyntax) {
					return "\"" + value.replace("\"", "\"\"") + "\"";
				} else {
					return value;
				}
			}
		}
	}

	/**
	 * Joins column names separated by ", " after quoting them if needed (see {@link #escapeVendorReservedNames(DbVendor, String)}).
	 * Expressions with brackets like {@code LOWER(email)} are not quoted.
	 *
	 * @param dbVendor database vendor
	 * @param columnNames column names or expressions
	 * @return joined column names
	 */
	public static String joinColumnVendorEscaped(final DbVendor dbVendor, final Collection<String> columnNames) {
		final StringBuilder returnValue = new StringBuilder();
		for (final String columnName : columnNames) {
			if (returnValue.length() > 0) {
				returnValue.append(", ");
			}
			returnValue.append(escapeColumnNameOrExpression(dbVendor, columnName));
		}
		return returnValue.toString();
	}

	/**
	 * Escapes a plain column name if needed (see {@link #escapeVendorReservedNames(DbVendor, String)}), but leaves
	 * column expressions with functions like {@code LOWER(email)} untouched, because quoting them would turn
	 * the whole expression into a single (non existing) column name.
	 *
	 * @param dbVendor database vendor
	 * @param columnNameOrExpression plain column name or expression with brackets
	 * @return escaped column name or the unchanged expression
	 */
	private static String escapeColumnNameOrExpression(final DbVendor dbVendor, final String columnNameOrExpression) {
		if (columnNameOrExpression != null && columnNameOrExpression.contains("(")) {
			return columnNameOrExpression.trim();
		} else {
			return escapeVendorReservedNames(dbVendor, columnNameOrExpression);
		}
	}

	/**
	 * Shuts down a Derby database, so the single user database is free for the next connection, for example of another thread.
	 *
	 * @param dbName path of the Derby database directory ({@code ~} is replaced by the user's home directory)
	 * @throws DbNotExistsException if the database directory does not exist
	 * @throws Exception if the path is no directory or the shutdown fails
	 */
	public static void shutDownDerbyDb(String dbName) throws Exception {
		dbName = Utilities.replaceUsersHome(dbName);
		if (!new File(dbName).exists()) {
			throw new DbNotExistsException("Derby database directory '" + dbName + "' is not available");
		} else if (!new File(dbName).isDirectory()) {
			throw new Exception("Derby database directory '" + dbName + "' is not a directory");
		}
		try (Connection connection = DriverManager.getConnection("jdbc:derby:" + Utilities.replaceUsersHome(dbName) + ";shutdown=true")) {
			// do nothing
		} catch (@SuppressWarnings("unused") final SQLNonTransientConnectionException e) {
			// Derby shutdown ALWAYS throws a SQLNonTransientConnectionException by intention (see also: http://db.apache.org/derby/docs/10.3/devguide/tdevdvlp20349.html)
		}
	}

	/**
	 * Returns the MySQL server variable "max_allowed_packet".
	 *
	 * @param connection MySQL or MariaDB database connection
	 * @return maximum packet size in bytes, 0 if not available
	 * @throws Exception if the variable cannot be read
	 */
	public static int getMysqlMaxAllowedPacketSize(final Connection connection) throws Exception {
		try (PreparedStatement preparedStatement = connection.prepareStatement("SHOW VARIABLES like 'max_allowed_packet'");
				ResultSet resultSet = preparedStatement.executeQuery()) {
			if (resultSet.next()) {
				final Object value = resultSet.getObject("value");
				if (value != null) {
					return Integer.parseInt(value.toString());
				} else {
					return 0;
				}
			} else {
				return 0;
			}
		}
	}

	/**
	 * Checks if a String is a simple identifier with 1 to 30 letters, digits or underscores.
	 *
	 * @param identifier identifier to check
	 * @return true if the identifier is valid, false otherwise (also for null)
	 */
	public static boolean isValidIdentifier(final String identifier) {
		if (identifier == null) {
			return false;
		}
		final Pattern pattern = Pattern.compile("[0-9A-Za-z_]{1,30}");
		return pattern.matcher(identifier).matches();
	}

	/**
	 * Checks if a String is a simple alias with 1 to 30 letters, digits or underscores.
	 *
	 * @param alias alias to check
	 * @return true if the alias is valid, false otherwise (also for null)
	 */
	public static boolean isValidAlias(final String alias) {
		if (alias == null) {
			return false;
		}
		final Pattern pattern = Pattern.compile("[0-9A-Za-z_]{1,30}");
		return pattern.matcher(alias).matches();
	}

	/**
	 * Stores the content of a file in a BLOB column. For MySQL and MariaDB the file size is checked against
	 * the server variable "max_allowed_packet" before.
	 *
	 * @param dbDefinition connection parameters
	 * @param sqlUpdateStatementWithPlaceholder SQL statement with a single placeholder "?" for the file content
	 * @param filePath path of the file
	 * @throws Exception if the file is too big for the server or cannot be stored
	 */
	public static void updateBlob(final DbConnectionDefinition dbDefinition, final String sqlUpdateStatementWithPlaceholder, final String filePath) throws Exception {
		try (Connection connection = createConnection(dbDefinition, false)) {
			if (dbDefinition.getDbVendor() == DbVendor.MySQL) {
				final long maxPacketSize = getMysqlConnectionNumericVariable(connection, "max_allowed_packet");
				if (maxPacketSize > 0 && new File(filePath).length() > maxPacketSize) {
					throw new Exception("File size is too big for current database settings. Please adjust MySQL server variable 'max_allowed_packet' to at least " + new File(filePath).length());
				}
			} else if (dbDefinition.getDbVendor() == DbVendor.MariaDB) {
				final long maxPacketSize = getMariaDBConnectionNumericVariable(connection, "max_allowed_packet");
				if (maxPacketSize > 0 && new File(filePath).length() > maxPacketSize) {
					throw new Exception("File size is too big for current database settings. Please adjust MariaDB server variable 'max_allowed_packet' to at least " + new File(filePath).length());
				}
			}

			try (FileInputStream inputStream = new FileInputStream(new File(filePath))) {
				try (PreparedStatement preparedStatement = connection.prepareStatement(sqlUpdateStatementWithPlaceholder)) {
					preparedStatement.setBinaryStream(1, inputStream);
					preparedStatement.execute();
				} catch (final Exception e) {
					throw e;
				}
			}
		}
	}

	/**
	 * Returns a numeric MySQL server variable.
	 *
	 * @param connection MySQL database connection
	 * @param variableName name of the variable
	 * @return value of the variable, 0 if not available
	 * @throws SQLException if the variable cannot be read
	 */
	public static long getMysqlConnectionNumericVariable(final Connection connection, final String variableName) throws SQLException {
		try (PreparedStatement preparedStatement = connection.prepareStatement("SHOW VARIABLES WHERE variable_name = ?")) {
			preparedStatement.setString(1, variableName);
			try (ResultSet resultSet = preparedStatement.executeQuery()) {
				if (resultSet.next()) {
					final Object value = resultSet.getObject("value");
					if (value != null) {
						return Long.parseLong(value.toString());
					} else {
						return 0;
					}
				} else {
					return 0;
				}
			}
		}
	}

	/**
	 * Returns a numeric MariaDB server variable.
	 *
	 * @param connection MariaDB database connection
	 * @param variableName name of the variable
	 * @return value of the variable, 0 if not available
	 * @throws SQLException if the variable cannot be read
	 */
	public static long getMariaDBConnectionNumericVariable(final Connection connection, final String variableName) throws SQLException {
		try (PreparedStatement preparedStatement = connection.prepareStatement("SHOW VARIABLES WHERE variable_name = ?")) {
			preparedStatement.setString(1, variableName);
			try (ResultSet resultSet = preparedStatement.executeQuery()) {
				if (resultSet.next()) {
					final Object value = resultSet.getObject("value");
					if (value != null) {
						return Long.parseLong(value.toString());
					} else {
						return 0;
					}
				} else {
					return 0;
				}
			}
		}
	}

	/**
	 * Sets a numeric global MySQL server variable (requires the privilege to set global variables).
	 *
	 * @param connection MySQL database connection
	 * @param variableName name of the variable (concatenated into the statement, must be trusted)
	 * @param variableValue new value
	 * @throws SQLException if the variable cannot be set
	 */
	public static void setMaysqlDBConnectionNumericVariable(final Connection connection, final String variableName, final long variableValue) throws SQLException {
		try (PreparedStatement preparedStatement = connection.prepareStatement("SET GLOBAL " + variableName + " = ?")) {
			preparedStatement.setLong(1, variableValue);
			preparedStatement.execute();
		}
	}

	/**
	 * Sets a numeric global MariaDB server variable (requires the privilege to set global variables).
	 *
	 * @param connection MariaDB database connection
	 * @param variableName name of the variable (concatenated into the statement, must be trusted)
	 * @param variableValue new value
	 * @throws SQLException if the variable cannot be set
	 */
	public static void setMariaDBConnectionNumericVariable(final Connection connection, final String variableName, final long variableValue) throws SQLException {
		try (PreparedStatement preparedStatement = connection.prepareStatement("SET GLOBAL " + variableName + " = ?")) {
			preparedStatement.setLong(1, variableValue);
			preparedStatement.execute();
		}
	}

	/**
	 * Requests a recalculation of the Oracle table statistics, so the table indices are used after the creation of big temporary tables.
	 * Does nothing for other database vendors.
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @throws Exception if the statistics cannot be gathered
	 */
	public static void gatherTableStats(final Connection connection, final String tableName) throws Exception {
		if (getDbVendor(connection) == DbVendor.Oracle) {
			try (Statement statement = connection.createStatement()) {
				String username;
				try (ResultSet resultSet = statement.executeQuery("SELECT USER FROM DUAL")) {
					if (resultSet.next()) {
						username = resultSet.getString(1);
					} else {
						throw new Exception("Cannot detect oracle database username");
					}
				}

				// Owner and table name as bind variables, so the table name cannot inject PL/SQL
				final String executeStatement =
						"begin\n"
								+ " dbms_stats.gather_table_stats(\n"
								+ " ownname => ?,\n"
								+ " tabname => ?,\n"
								+ " estimate_percent => 30,\n"
								+ " method_opt => 'for all columns size 254',\n"
								+ " cascade => true,\n"
								+ " no_invalidate => FALSE\n"
								+ " );\n"
								+ " end;";

				// execute() returns false for a PL/SQL block (no ResultSet), so only an SQLException indicates a failure
				try (CallableStatement callableStatement = connection.prepareCall(executeStatement)) {
					callableStatement.setString(1, username.toUpperCase());
					callableStatement.setString(2, unescapeVendorReservedNames(DbVendor.Oracle, tableName.trim()).toUpperCase());
					callableStatement.execute();
				} catch (final SQLException e) {
					throw new Exception("Cannot gatherTableStats for table " + tableName + ": " + e.getMessage(), e);
				}
			}
		}
	}

	/**
	 * Returns the storage size of a table. Supported for Oracle (LOB and index segments), MySQL and MariaDB (data length and free space).
	 *
	 * @param connection database connection
	 * @param tableName name of the table
	 * @return storage size in bytes
	 * @throws Exception if the vendor is not supported or the size cannot be read
	 */
	public static long getTableStorageSize(final Connection connection, final String tableName) throws Exception {
		if (getDbVendor(connection) == DbVendor.Oracle) {
			final List<String> indexNames = getTableIndexNames(connection, tableName);
			final String sql = "SELECT SUM(bytes) FROM user_segments WHERE segment_name in (SELECT segment_name FROM user_lobs WHERE table_name = ?) OR segment_name IN (?" + Utilities.repeat(", ?", indexNames.size()) + ")";
			try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
				preparedStatement.setNString(1, tableName.toUpperCase());
				preparedStatement.setNString(2, tableName.toUpperCase());
				for (int i = 0; i < indexNames.size(); i++) {
					preparedStatement.setNString(3 + i, indexNames.get(i).toUpperCase());
				}
				try (ResultSet resultSet = preparedStatement.executeQuery()) {
					if (resultSet.next()) {
						return resultSet.getLong(1);
					} else {
						throw new Exception("Invalid data for: " + tableName);
					}
				}
			}
		} else if (getDbVendor(connection) == DbVendor.MariaDB || getDbVendor(connection) == DbVendor.MySQL) {
			final String sql = "SELECT data_length + data_free FROM information_schema.tables WHERE LOWER(table_name) = ?";
			try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
				preparedStatement.setNString(1, tableName.toLowerCase());
				try (ResultSet resultSet = preparedStatement.executeQuery()) {
					if (resultSet.next()) {
						return resultSet.getLong(1);
					} else {
						throw new Exception("Invalid data for: " + tableName);
					}
				}
			}
		} else {
			throw new Exception("getTableStorageSize is not supported by this database vendor");
		}
	}

	/**
	 * Returns the names of the indices of a table. Supported for Oracle, MySQL, MariaDB and PostgreSQL.
	 *
	 * @param connection database connection
	 * @param tableName name of the table, optionally with schema prefix for PostgreSQL
	 * @return index names
	 * @throws Exception if the vendor is not supported or the indices cannot be read
	 */
	public static List<String> getTableIndexNames(final Connection connection, final String tableName) throws Exception {
		if (getDbVendor(connection) == DbVendor.Oracle) {
			final String query = "SELECT index_name FROM user_ind_columns WHERE LOWER(table_name) = ?";
			try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
				preparedStatement.setNString(1, tableName.toLowerCase());
				try (ResultSet resultSet = preparedStatement.executeQuery()) {
					final List<String> resultList = new ArrayList<>();
					while (resultSet.next()) {
						resultList.add(resultSet.getString(1));
					}
					return resultList;
				}
			}
		} else if (getDbVendor(connection) == DbVendor.MariaDB || getDbVendor(connection) == DbVendor.MySQL) {
			final String query = "SHOW INDEX FROM " + tableName.toLowerCase();
			try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
				try (ResultSet resultSet = preparedStatement.executeQuery()) {
					final List<String> resultList = new ArrayList<>();
					while (resultSet.next()) {
						// Column 1 of "SHOW INDEX" is the table name, the index name is in column "Key_name" (one row per indexed column)
						final String indexName = resultSet.getString("Key_name");
						if (!resultList.contains(indexName)) {
							resultList.add(indexName);
						}
					}
					return resultList;
				}
			}
		} else if (getDbVendor(connection) == DbVendor.PostgreSQL) {
			String schemaName;
			String tableNameWithoutSchema;
			if (tableName.contains(".")) {
				schemaName = tableName.substring(0, tableName.lastIndexOf("."));
				tableNameWithoutSchema = tableName.substring(tableName.lastIndexOf(".") + 1);
			} else {
				schemaName = null;
				tableNameWithoutSchema = tableName;
			}
			try (Statement statement = connection.createStatement()) {
				try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM pg_indexes WHERE " + (schemaName != null ? "LOWER(schemaname) = LOWER(?) AND " : "") + "tablename = ?")) {
					if (schemaName == null) {
						preparedStatement.setString(1, tableNameWithoutSchema);
					} else {
						preparedStatement.setString(1, schemaName);
						preparedStatement.setString(2, tableNameWithoutSchema);
					}

					try (ResultSet resultSet = preparedStatement.executeQuery()) {
						final List<String> resultList = new ArrayList<>();
						while (resultSet.next()) {
							final String indexName = resultSet.getString("indexname").toLowerCase();
							resultList.add(indexName);
						}
						return resultList;
					}
				}
			}
		} else {
			throw new Exception("getTableIndexNames is not supported by this database vendor");
		}
	}

	/**
	 * Enables or disables all foreign key constraints. Supported for Oracle (all non system owners, errors of single
	 * constraints are only printed) and MySQL/MariaDB (FOREIGN_KEY_CHECKS of the session).
	 *
	 * @param dbVendor database vendor
	 * @param connection database connection
	 * @param activated true to enable, false to disable the constraints
	 * @throws Exception if the vendor is not supported or some constraints could not be enabled
	 */
	public static void setForeignKeyConstraintStatus(final DbVendor dbVendor, final Connection connection, final boolean activated) throws Exception {
		if (dbVendor == DbVendor.Oracle) {
			final List<String> sqlListToExecute = new ArrayList<>();
			try (Statement statement = connection.createStatement()) {
				try (ResultSet result = statement.executeQuery("SELECT owner, table_name, constraint_name FROM all_constraints WHERE constraint_type = 'R' AND status = '" + (activated ? "DISABLED" : "ENABLED") + "' AND UPPER(owner) NOT IN ('SYS', 'SYSTEM', 'CTXSYS', 'SITE_SYS', 'MDSYS')")) {
					while (result.next()) {
						final String owner = result.getString("owner");
						final String tableName = result.getString("table_name");
						final String constraintName = result.getString("constraint_name");

						// all_constraints also contains tables of other owners, so the table name must be qualified by its owner.
						// Dictionary names are stored exactly, so they are quoted to also support mixed case names.
						sqlListToExecute.add("ALTER TABLE \"" + owner + "\".\"" + tableName + "\" " + (activated ? "ENABLE" : "DISABLE") + " CONSTRAINT \"" + constraintName + "\"");
					}
				}

				for (final String sqlToExecute : sqlListToExecute) {
					try {
						statement.execute(sqlToExecute);
					} catch (final SQLException e) {
						e.printStackTrace();
					}
				}

				if (activated) {
					final List<String> notEnabledConstraints = new ArrayList<>();
					try (ResultSet result = statement.executeQuery("SELECT table_name, constraint_name FROM all_constraints WHERE constraint_type = 'R' AND status != 'ENABLED' AND UPPER(owner) NOT IN ('SYS', 'SYSTEM', 'CTXSYS', 'SITE_SYS', 'MDSYS')")) {
						while (result.next()) {
							final String tableName = result.getString("table_name");
							final String constraintName = result.getString("constraint_name");
							notEnabledConstraints.add(constraintName + " (TABLE: " + tableName + ")");
						}
					}
					if (notEnabledConstraints.size() > 0) {
						throw new Exception("Cannot activate following foreign key constraints:\n" + Utilities.join(notEnabledConstraints, ",\n"));
					}
				}
			}
		} else if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB) {
			try (Statement statement = connection.createStatement()) {
				if (activated) {
					statement.execute("SET FOREIGN_KEY_CHECKS = 1");
				} else {
					statement.execute("SET FOREIGN_KEY_CHECKS = 0");
				}
			}
		} else {
			throw new Exception("ForeignKeyConstraintStatus change not available for dbvendor '" + dbVendor.name() + "'");
		}
	}

	/**
	 * Enables or disables all triggers of the current Oracle user. Errors of single triggers are only printed.
	 *
	 * @param dbVendor database vendor (only Oracle is supported)
	 * @param connection database connection
	 * @param activated true to enable, false to disable the triggers
	 * @throws Exception if the vendor is not supported or some triggers could not be enabled
	 */
	public static void setTriggerStatus(final DbVendor dbVendor, final Connection connection, final boolean activated) throws Exception {
		if (dbVendor == DbVendor.Oracle) {
			final List<String> sqlListToExecute = new ArrayList<>();
			try (Statement statement = connection.createStatement()) {
				try (ResultSet result = statement.executeQuery("SELECT trigger_name FROM user_triggers WHERE status = '" + (activated ? "DISABLED" : "ENABLED") + "'")) {
					while (result.next()) {
						final String triggerName = result.getString("trigger_name");

						sqlListToExecute.add("ALTER TRIGGER " + triggerName + " " + (activated ? "ENABLE" : "DISABLE"));
					}
				}

				for (final String sqlToExecute : sqlListToExecute) {
					try {
						statement.execute(sqlToExecute);
					} catch (final SQLException e) {
						e.printStackTrace();
					}
				}

				if (activated) {
					final List<String> notEnabledTriggers = new ArrayList<>();
					try (ResultSet result = statement.executeQuery("SELECT trigger_name FROM user_triggers WHERE status = 'DISABLED'")) {
						while (result.next()) {
							final String triggerName = result.getString("trigger_name");
							notEnabledTriggers.add(triggerName);
						}
					}
					if (notEnabledTriggers.size() > 0) {
						throw new Exception("Cannot activate following triggers:\n" + Utilities.join(notEnabledTriggers, ",\n"));
					}
				}
			}
		} else {
			throw new Exception("TriggerStatus change not available for dbvendor '" + dbVendor.name() + "'");
		}
	}

	/**
	 * Creates a statement for reading large result sets. For MySQL the rows are streamed one by one,
	 * for other vendors a fetch size of 100 is used.
	 *
	 * @param connection database connection
	 * @return new statement, to be closed by the caller
	 * @throws Exception if the statement cannot be created
	 */
	public static Statement getStatementForLargeQuery(final Connection connection) throws Exception {
		final DbVendor dbVendor = getDbVendor(connection);
		if (DbVendor.MySQL == dbVendor) {
			final Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			statement.setFetchSize(Integer.MIN_VALUE);
			return statement;
		} else {
			final Statement statement = connection.createStatement();
			statement.setFetchSize(100);
			return statement;
		}
	}

	/**
	 * Returns the foreign keys of a table via JDBC metadata, one entry per foreign key column. All names are lowercased.
	 *
	 * @param connection database connection
	 * @param tableName name of the table, optionally with schema prefix
	 * @return foreign key column references, null if the table name is blank
	 * @throws Exception if the foreign keys cannot be read
	 */
	public static List<DatabaseForeignKey> getForeignKeys(final Connection connection, String tableName) throws Exception {
		if (Utilities.isBlank(tableName)) {
			return null;
		} else {
			final DbVendor dbVendor = getDbVendor(connection);
			try {
				if (dbVendor == DbVendor.Oracle || dbVendor == DbVendor.HSQL || dbVendor == DbVendor.Derby) {
					tableName = tableName.toUpperCase();
				}

				String schemaName;
				String tableNameWithoutSchema;
				if (tableName.contains(".")) {
					schemaName = tableName.substring(0, tableName.lastIndexOf("."));
					tableNameWithoutSchema = tableName.substring(tableName.lastIndexOf(".") + 1);
				} else {
					schemaName = null;
					tableNameWithoutSchema = tableName;
				}

				final DatabaseMetaData metaData = connection.getMetaData();
				try (ResultSet resultSet = metaData.getImportedKeys(connection.getCatalog(), schemaName, tableNameWithoutSchema)) {
					final List<DatabaseForeignKey> returnList = new ArrayList<>();
					while (resultSet.next()) {
						String foreignKeyName = resultSet.getString("FK_NAME");
						if (foreignKeyName != null) {
							foreignKeyName = foreignKeyName.toLowerCase();
						}
						final String columnName = resultSet.getString("FKCOLUMN_NAME");
						final String referencesTableName = resultSet.getString("PKTABLE_NAME");
						final String referencedColumnName = resultSet.getString("PKCOLUMN_NAME");
						returnList.add(new DatabaseForeignKey(foreignKeyName, tableName.toLowerCase(), columnName.toLowerCase(), referencesTableName.toLowerCase(), referencedColumnName.toLowerCase()));
					}
					return returnList;
				}
			} catch (final Exception e) {
				throw new Exception("Cannot read foreign key columns for table " + tableName + ": " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Returns the constraints of a table. Supported for Oracle (primary key, unique, foreign key and check constraints
	 * with their columns), MySQL, MariaDB (with check conditions) and PostgreSQL. All names are lowercased.
	 *
	 * @param connection database connection
	 * @param tableName name of the table, optionally with schema prefix for MySQL, MariaDB and PostgreSQL
	 * @return constraints, null if the table name is blank or the vendor is not supported
	 * @throws Exception if the constraints cannot be read
	 */
	public static List<DatabaseConstraint> getConstraints(final Connection connection, String tableName) throws Exception {
		if (Utilities.isBlank(tableName)) {
			return null;
		} else {
			final DbVendor dbVendor = getDbVendor(connection);
			try {
				if (dbVendor == DbVendor.Oracle || dbVendor == DbVendor.HSQL || dbVendor == DbVendor.Derby) {
					tableName = tableName.toUpperCase();
				}

				if (dbVendor == DbVendor.Oracle) {
					try (PreparedStatement preparedStatement = connection.prepareStatement(
							"SELECT user_constraints.constraint_name, user_constraints.constraint_type, user_cons_columns.column_name"
									+ " FROM user_constraints JOIN user_cons_columns ON user_constraints.constraint_name = user_cons_columns.constraint_name AND user_constraints.table_name = user_cons_columns.table_name"
									+ " WHERE user_constraints.table_name = ? AND user_constraints.constraint_type IN ('P', 'U', 'R', 'C') ORDER BY user_constraints.constraint_name")) {
						preparedStatement.setNString(1, tableName);
						try (ResultSet resultSet = preparedStatement.executeQuery()) {
							final List<DatabaseConstraint> returnList = new ArrayList<>();
							while (resultSet.next()) {
								final String constraintName = resultSet.getString("constraint_name").toLowerCase();
								final String constraintType = resultSet.getString("constraint_type");
								final String columnName = resultSet.getString("column_name");

								returnList.add(new DatabaseConstraint(tableName.toLowerCase(), constraintName, ConstraintType.fromName(constraintType), columnName.toLowerCase(), null));
							}
							return returnList;
						}
					}
				} else if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB) {
					String schemaName;
					String tableNameWithoutSchema;
					if (tableName.contains(".")) {
						schemaName = tableName.substring(0, tableName.lastIndexOf("."));
						tableNameWithoutSchema = tableName.substring(tableName.lastIndexOf(".") + 1);
					} else {
						schemaName = null;
						tableNameWithoutSchema = tableName;
					}

					final List<DatabaseConstraint> returnList = new ArrayList<>();
					try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT constraint_name, constraint_type FROM information_schema.table_constraints"
							+ " WHERE " + (schemaName != null ? "LOWER(table_schema) = LOWER(?)" : "table_schema = DATABASE()") + " AND table_name = ? ORDER BY constraint_name")) {
						if (schemaName == null) {
							preparedStatement.setNString(1, tableNameWithoutSchema);
						} else {
							preparedStatement.setNString(1, schemaName);
							preparedStatement.setNString(2, tableNameWithoutSchema);
						}
						try (ResultSet resultSet = preparedStatement.executeQuery()) {
							while (resultSet.next()) {
								final String constraintName = resultSet.getString("constraint_name").toLowerCase();
								final String constraintType = resultSet.getString("constraint_type");

								returnList.add(new DatabaseConstraint(tableName.toLowerCase(), constraintName, ConstraintType.fromName(constraintType), null, null));
							}
						}
					}

					// information_schema.check_constraints of MySQL has no table_name/table_schema columns, so it is joined with table_constraints
					try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT cc.constraint_name, cc.check_clause FROM information_schema.check_constraints cc"
							+ " JOIN information_schema.table_constraints tc ON tc.constraint_schema = cc.constraint_schema AND tc.constraint_name = cc.constraint_name"
							+ " WHERE " + (schemaName != null ? "LOWER(tc.table_schema) = LOWER(?)" : "tc.table_schema = DATABASE()") + " AND tc.table_name = ?")) {
						if (schemaName == null) {
							preparedStatement.setNString(1, tableNameWithoutSchema);
						} else {
							preparedStatement.setNString(1, schemaName);
							preparedStatement.setNString(2, tableNameWithoutSchema);
						}
						try (ResultSet resultSet = preparedStatement.executeQuery()) {
							while (resultSet.next()) {
								final String constraintName = resultSet.getString("constraint_name").toLowerCase();
								final String condition = resultSet.getString("check_clause");

								for (final DatabaseConstraint databaseConstraint : returnList) {
									if (constraintName != null && constraintName.equalsIgnoreCase(databaseConstraint.getConstraintName())) {
										databaseConstraint.setCondition(condition);
									}
								}
							}
						}
					} catch (final Exception e) {
						System.err.println("Cannot read constraint conditions of table '" + tableName + "': " + e.getMessage());
					}

					return returnList;
				} else if (dbVendor == DbVendor.PostgreSQL) {
					String schemaName;
					String tableNameWithoutSchema;
					if (tableName.contains(".")) {
						schemaName = tableName.substring(0, tableName.lastIndexOf("."));
						tableNameWithoutSchema = tableName.substring(tableName.lastIndexOf(".") + 1);
					} else {
						schemaName = null;
						tableNameWithoutSchema = tableName;
					}

					final List<DatabaseConstraint> returnList = new ArrayList<>();
					try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT constraint_name, constraint_type FROM information_schema.table_constraints"
							+ " WHERE " + (schemaName != null ? "LOWER(table_schema) = LOWER(?) AND " : "") + "table_name = ? ORDER BY constraint_name")) {
						if (schemaName == null) {
							preparedStatement.setString(1, tableNameWithoutSchema);
						} else {
							preparedStatement.setString(1, schemaName);
							preparedStatement.setString(2, tableNameWithoutSchema);
						}
						try (ResultSet resultSet = preparedStatement.executeQuery()) {
							while (resultSet.next()) {
								final String constraintName = resultSet.getString("constraint_name").toLowerCase();
								final String constraintType = resultSet.getString("constraint_type");

								returnList.add(new DatabaseConstraint(tableName.toLowerCase(), constraintName, ConstraintType.fromName(constraintType), null, null));
							}
						}
					}

					return returnList;
				} else {
					return null;
				}
			} catch (final Exception e) {
				throw new Exception("Cannot read constraints columns for table " + tableName + ": " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Returns the indices of a table with their columns. Supported for Oracle, MySQL, MariaDB and PostgreSQL.
	 * All names are lowercased, an index named "primary" is listed first.
	 *
	 * @param connection database connection
	 * @param tableName name of the table, optionally with schema prefix for PostgreSQL
	 * @return indices, null if the table name is blank or the vendor is not supported
	 * @throws Exception if the indices cannot be read
	 */
	public static List<DatabaseIndex> getIndices(final Connection connection, String tableName) throws Exception {
		if (Utilities.isBlank(tableName)) {
			return null;
		} else {
			final DbVendor dbVendor = getDbVendor(connection);
			try {
				if (dbVendor == DbVendor.Oracle || dbVendor == DbVendor.HSQL || dbVendor == DbVendor.Derby) {
					tableName = tableName.toUpperCase();
				}

				if (dbVendor == DbVendor.Oracle) {
					try (Statement statement = connection.createStatement()) {
						try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT index_name, column_name FROM all_ind_columns WHERE table_name = ?")) {
							preparedStatement.setNString(1, tableName);
							try (ResultSet resultSet = preparedStatement.executeQuery()) {
								final Map<String, List<String>> interimMap = new HashMap<>();

								final List<DatabaseIndex> returnList = new ArrayList<>();
								while (resultSet.next()) {
									final String indexName = resultSet.getString("index_name").toLowerCase();
									final String columnName = resultSet.getString("column_name".toLowerCase());

									if (!interimMap.containsKey(indexName)) {
										interimMap.put(indexName, new ArrayList<>());
									}

									interimMap.get(indexName).add(columnName.toLowerCase());
								}

								for (final String indexName : Utilities.sortButPutItemsFirst(interimMap.keySet(), "primary")) {
									returnList.add(new DatabaseIndex(tableName.toLowerCase(), indexName, interimMap.get(indexName)));
								}
								return returnList;
							}
						}
					}
				} else if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB) {
					try (Statement statement = connection.createStatement()) {
						try (ResultSet resultSet = statement.executeQuery("SHOW index FROM " + tableName)) {
							final Map<String, List<String>> interimMap = new HashMap<>();

							final List<DatabaseIndex> returnList = new ArrayList<>();
							while (resultSet.next()) {
								final String indexName = resultSet.getString("key_name").toLowerCase();
								final String columnName = resultSet.getString("column_name".toLowerCase());

								if (!interimMap.containsKey(indexName)) {
									interimMap.put(indexName, new ArrayList<>());
								}

								interimMap.get(indexName).add(columnName.toLowerCase());
							}

							for (final String indexName : Utilities.sortButPutItemsFirst(interimMap.keySet(), "primary")) {
								returnList.add(new DatabaseIndex(tableName.toLowerCase(), indexName, interimMap.get(indexName)));
							}
							return returnList;
						}
					}
				} else if (dbVendor == DbVendor.PostgreSQL) {
					String schemaName;
					String tableNameWithoutSchema;
					if (tableName.contains(".")) {
						schemaName = tableName.substring(0, tableName.lastIndexOf("."));
						tableNameWithoutSchema = tableName.substring(tableName.lastIndexOf(".") + 1);
					} else {
						schemaName = null;
						tableNameWithoutSchema = tableName;
					}

					try (Statement statement = connection.createStatement()) {
						try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT t.relnamespace, i.relname AS index_name, a.attname as column_name"
								+ " FROM pg_class t, pg_class i, pg_index ix, pg_attribute a"
								+ " WHERE t.oid = ix.indrelid AND i.oid = ix.indexrelid AND a.attrelid = t.oid AND a.attnum = ANY(ix.indkey) AND t.relkind = 'r'"
								+ (schemaName != null ? " AND t.relnamespace = ?::regnamespace" : "") + " AND LOWER(t.relname) = LOWER(?)"
								+ " ORDER BY t.relname, i.relname")) {
							if (schemaName == null) {
								preparedStatement.setString(1, tableNameWithoutSchema);
							} else {
								preparedStatement.setString(1, schemaName);
								preparedStatement.setString(2, tableNameWithoutSchema);
							}

							final Map<String, List<String>> interimMap = new HashMap<>();

							final List<DatabaseIndex> returnList = new ArrayList<>();
							try (ResultSet resultSet = preparedStatement.executeQuery()) {
								while (resultSet.next()) {
									final String indexName = resultSet.getString("index_name").toLowerCase();
									final String columnName = resultSet.getString("column_name".toLowerCase());

									if (!interimMap.containsKey(indexName)) {
										interimMap.put(indexName, new ArrayList<>());
									}

									interimMap.get(indexName).add(columnName.toLowerCase());
								}
							}

							for (final String indexName : Utilities.sortButPutItemsFirst(interimMap.keySet(), "primary")) {
								returnList.add(new DatabaseIndex(tableName.toLowerCase(), indexName, interimMap.get(indexName)));
							}
							return returnList;
						}
					}
				} else {
					return null;
				}
			} catch (final Exception e) {
				throw new Exception("Cannot read indexed columns for table " + tableName + ": " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Checks if a schema exists. Supported for MySQL, MariaDB and PostgreSQL.
	 *
	 * @param connection database connection
	 * @param schemaName name of the schema
	 * @return true if the schema exists, false if not or if the name is blank
	 * @throws Exception if the vendor is not supported or the schemas cannot be read
	 */
	public static boolean checkSchemaExist(final Connection connection, final String schemaName) throws Exception {
		if (Utilities.isBlank(schemaName)) {
			return false;
		} else {
			final DbVendor dbVendor = getDbVendor(connection);
			try {
				if (dbVendor == DbVendor.MySQL || dbVendor == DbVendor.MariaDB || dbVendor == DbVendor.PostgreSQL) {
					try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
						preparedStatement.setString(1, schemaName);
						try (ResultSet resultSet = preparedStatement.executeQuery()) {
							if (resultSet.next()) {
								return true;
							} else {
								return false;
							}
						}
					}
				} else {
					throw new Exception("Unsupported db vendor for checkSchemaExist: " + dbVendor.name());
				}
			} catch (final Exception e) {
				throw new Exception("Cannot check for existing schema '" + schemaName + "': " + e.getMessage(), e);
			}
		}
	}
}
