package de.catma.repository.neo4j.model_wrappers;

import de.catma.repository.neo4j.Neo4JRelationshipType;
import de.catma.tag.TagDefinition;
import de.catma.tag.TagsetDefinition;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.util.ArrayList;
import java.util.List;

@NodeEntity(label="Tagset")
public class Neo4JTagset {
	@Id
	@GeneratedValue
	private Long id;

	private String uuid;
	private String name;
	private String revisionHash;

	@Relationship(type=Neo4JRelationshipType.HAS_TAG_DEFINITION, direction=Relationship.OUTGOING)
	private List<Neo4JTagDefinition> tagDefinitions;

	public Neo4JTagset() {
		this.tagDefinitions = new ArrayList<>();
	}

	public Neo4JTagset(TagsetDefinition tagsetDefinition) {
		this();

		this.setTagsetDefinition(tagsetDefinition);
	}

	public Long getId() {
		return this.id;
	}

	public String getUuid() {
		return this.uuid;
	}

	public String getName() {
		return this.name;
	}

	public String getRevisionHash() {
		return this.revisionHash;
	}

	public TagsetDefinition getTagsetDefinition() {
		TagsetDefinition tagsetDefinition = new TagsetDefinition();
		tagsetDefinition.setRevisionHash(this.revisionHash);
		tagsetDefinition.setUuid(this.uuid);
		tagsetDefinition.setName(this.name);

		for(Neo4JTagDefinition neo4JTagDefinition : this.tagDefinitions) {
			tagsetDefinition.addTagDefinition(neo4JTagDefinition.getTagDefinition());
		}

		return tagsetDefinition;
	}

	public void setTagsetDefinition(TagsetDefinition tagsetDefinition) {
		this.revisionHash = tagsetDefinition.getRevisionHash();
		this.uuid = tagsetDefinition.getUuid();
		this.name = tagsetDefinition.getName();

		this.tagDefinitions.clear();
		for (TagDefinition tagDefinition : tagsetDefinition) {
			this.tagDefinitions.add(
					new Neo4JTagDefinition(tagDefinition, tagsetDefinition.getDirectChildren(tagDefinition))
			);
		}
	}
}
