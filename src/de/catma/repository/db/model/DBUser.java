package de.catma.repository.db.model;

// Generated 22.05.2012 21:58:37 by Hibernate Tools 3.4.0.CR1

import static javax.persistence.GenerationType.IDENTITY;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import de.catma.user.User;

/**
 * User generated by hbm2java
 */
@Entity
@Table(name = "user", catalog = "CatmaRepository")
public class DBUser implements java.io.Serializable, User {

	private Integer userId;
	private String identifier;
	private boolean locked;
	
	private Set<DBUserSourceDocument> dbUserSourceDocuments = 
			new HashSet<DBUserSourceDocument>();
	
	private Set<DBUserUserMarkupCollection> dbUserUserMarkupCollections =
			new HashSet<DBUserUserMarkupCollection>();
	
	private Set<DBUserTagLibrary> dbUserTagLibraries = 
			new HashSet<DBUserTagLibrary>();
	
	private String name;

	public DBUser() {
	}

	public DBUser(String identifier) {
		this.identifier = identifier;
		this.locked = false;
	}

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "userID", unique = true, nullable = false)
	public Integer getUserId() {
		return this.userId;
	}

	public void setUserId(Integer userId) {
		this.userId = userId;
	}

	@Column(name = "identifier", nullable = false, length = 300)
	public String getIdentifier() {
		return this.identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	@Column(name = "locked", nullable = false)
	public boolean isLocked() {
		return this.locked;
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
	}
	
	@OneToMany(mappedBy = "dbUser")
	public Set<DBUserSourceDocument> getDbUserSourceDocuments() {
		return dbUserSourceDocuments;
	}

	public void setDbUserSourceDocuments(
			Set<DBUserSourceDocument> dbUserSourceDocuments) {
		this.dbUserSourceDocuments = dbUserSourceDocuments;
	}
	

	@OneToMany(mappedBy = "dbUser")
	public Set<DBUserUserMarkupCollection> getDbUserUserMarkupCollections() {
		return dbUserUserMarkupCollections;
	}
	
	public void setDbUserUserMarkupCollections(
			Set<DBUserUserMarkupCollection> dbUserUserMarkupCollections) {
		this.dbUserUserMarkupCollections = dbUserUserMarkupCollections;
	}
	
	@OneToMany(mappedBy = "dbUser")
	public Set<DBUserTagLibrary> getDbUserTagLibraries() {
		return dbUserTagLibraries;
	}

	public void setDbUserTagLibraries(Set<DBUserTagLibrary> dbUserTagLibraries) {
		this.dbUserTagLibraries = dbUserTagLibraries;
	}
	
	@Transient
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

}