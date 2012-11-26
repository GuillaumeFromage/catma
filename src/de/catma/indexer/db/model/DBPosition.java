package de.catma.indexer.db.model;

// Generated 04.05.2012 21:11:20 by Hibernate Tools 3.4.0.CR1

import static javax.persistence.GenerationType.IDENTITY;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import de.catma.document.Range;

/**
 * Position generated by hbm2java
 */
@Entity
@Table(name = "position", catalog = "CatmaIndex")
public class DBPosition implements java.io.Serializable {

	public static final String TABLENAME = "CatmaIndex.position";
	private Integer positionId;
	private DBTerm term;
	private int characterStart;
	private int characterEnd;
	private int tokenOffset;

	public DBPosition() {
	}

	public DBPosition(DBTerm term, int characterStart,
			int characterEnd, int tokenOffset) {
		this.term = term;
		this.characterStart = characterStart;
		this.characterEnd = characterEnd;
		this.tokenOffset = tokenOffset;
	}

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "positionID", unique = true, nullable = false)
	public Integer getPositionId() {
		return this.positionId;
	}

	void setPositionId(Integer positionId) {
		this.positionId = positionId;
	}
	
	@ManyToOne
    @JoinColumn(name="termID", nullable = false)
	public DBTerm getTerm() {
		return term;
	}

	void setTerm(DBTerm term) {
		this.term = term;
	}

	@Column(name = "characterStart", nullable = false)
	public int getCharacterStart() {
		return this.characterStart;
	}

	void setCharacterStart(int characterStart) {
		this.characterStart = characterStart;
	}

	@Column(name = "characterEnd", nullable = false)
	public int getCharacterEnd() {
		return this.characterEnd;
	}

	void setCharacterEnd(int characterEnd) {
		this.characterEnd = characterEnd;
	}

	@Column(name = "tokenOffset", nullable = false)
	public int getTokenOffset() {
		return this.tokenOffset;
	}

	void setTokenOffset(int tokenOffset) {
		this.tokenOffset = tokenOffset;
	}

	@Override
	public String toString() {
		return term.getTerm() + "@" + new Range(getCharacterStart(), getCharacterEnd());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + characterEnd;
		result = prime * result + characterStart;
		result = prime * result + ((term == null) ? 0 : term.hashCode());
		result = prime * result + tokenOffset;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DBPosition other = (DBPosition) obj;
		if (characterEnd != other.characterEnd)
			return false;
		if (characterStart != other.characterStart)
			return false;
		if (term == null) {
			if (other.term != null)
				return false;
		} else if (!term.equals(other.term))
			return false;
		if (tokenOffset != other.tokenOffset)
			return false;
		return true;
	}
	
	
}
