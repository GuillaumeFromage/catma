/**
 * This class is generated by jOOQ
 */
package de.catma.repository.db.jooqgen.catmarepository.tables;

/**
 * This class is generated by jOOQ.
 */
@javax.annotation.Generated(value    = { "http://www.jooq.org", "3.1.0" },
                            comments = "This class is generated by jOOQ")
@java.lang.SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class User extends org.jooq.impl.TableImpl<org.jooq.Record> {

	private static final long serialVersionUID = 115705415;

	/**
	 * The singleton instance of <code>CatmaRepository.user</code>
	 */
	public static final de.catma.repository.db.jooqgen.catmarepository.tables.User USER = new de.catma.repository.db.jooqgen.catmarepository.tables.User();

	/**
	 * The class holding records for this type
	 */
	@Override
	public java.lang.Class<org.jooq.Record> getRecordType() {
		return org.jooq.Record.class;
	}

	/**
	 * The column <code>CatmaRepository.user.userID</code>. 
	 */
	public final org.jooq.TableField<org.jooq.Record, java.lang.Integer> USERID = createField("userID", org.jooq.impl.SQLDataType.INTEGER, this);

	/**
	 * The column <code>CatmaRepository.user.identifier</code>. 
	 */
	public final org.jooq.TableField<org.jooq.Record, java.lang.String> IDENTIFIER = createField("identifier", org.jooq.impl.SQLDataType.VARCHAR.length(300), this);

	/**
	 * The column <code>CatmaRepository.user.locked</code>. 
	 */
	public final org.jooq.TableField<org.jooq.Record, java.lang.Byte> LOCKED = createField("locked", org.jooq.impl.SQLDataType.TINYINT, this);

	/**
	 * The column <code>CatmaRepository.user.email</code>. 
	 */
	public final org.jooq.TableField<org.jooq.Record, java.lang.String> EMAIL = createField("email", org.jooq.impl.SQLDataType.VARCHAR.length(300), this);

	/**
	 * The column <code>CatmaRepository.user.firstlogin</code>. 
	 */
	public final org.jooq.TableField<org.jooq.Record, java.sql.Timestamp> FIRSTLOGIN = createField("firstlogin", org.jooq.impl.SQLDataType.TIMESTAMP, this);

	/**
	 * The column <code>CatmaRepository.user.lastlogin</code>. 
	 */
	public final org.jooq.TableField<org.jooq.Record, java.sql.Timestamp> LASTLOGIN = createField("lastlogin", org.jooq.impl.SQLDataType.TIMESTAMP, this);

	/**
	 * The column <code>CatmaRepository.user.guest</code>. 
	 */
	public final org.jooq.TableField<org.jooq.Record, java.lang.Byte> GUEST = createField("guest", org.jooq.impl.SQLDataType.TINYINT, this);

	/**
	 * The column <code>CatmaRepository.user.spawnable</code>. 
	 */
	public final org.jooq.TableField<org.jooq.Record, java.lang.Byte> SPAWNABLE = createField("spawnable", org.jooq.impl.SQLDataType.TINYINT, this);

	/**
	 * Create a <code>CatmaRepository.user</code> table reference
	 */
	public User() {
		super("user", de.catma.repository.db.jooqgen.catmarepository.Catmarepository.CATMAREPOSITORY);
	}

	/**
	 * Create an aliased <code>CatmaRepository.user</code> table reference
	 */
	public User(java.lang.String alias) {
		super(alias, de.catma.repository.db.jooqgen.catmarepository.Catmarepository.CATMAREPOSITORY, de.catma.repository.db.jooqgen.catmarepository.tables.User.USER);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Identity<org.jooq.Record, java.lang.Integer> getIdentity() {
		return de.catma.repository.db.jooqgen.catmarepository.Keys.IDENTITY_USER;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.UniqueKey<org.jooq.Record> getPrimaryKey() {
		return de.catma.repository.db.jooqgen.catmarepository.Keys.KEY_USER_PRIMARY;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.util.List<org.jooq.UniqueKey<org.jooq.Record>> getKeys() {
		return java.util.Arrays.<org.jooq.UniqueKey<org.jooq.Record>>asList(de.catma.repository.db.jooqgen.catmarepository.Keys.KEY_USER_PRIMARY);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public de.catma.repository.db.jooqgen.catmarepository.tables.User as(java.lang.String alias) {
		return new de.catma.repository.db.jooqgen.catmarepository.tables.User(alias);
	}
}
