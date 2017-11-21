package de.catma.repository.neo4j.model_wrappers;

import de.catma.repository.neo4j.Neo4JOGMSessionFactory;
import de.catma.tag.PropertyDefinition;
import de.catma.tag.PropertyPossibleValueList;
import de.catma.tag.TagDefinition;
import de.catma.tag.TagsetDefinition;
import de.catma.util.IDGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.ogm.session.Session;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.*;

public class Neo4JTagsetTest {
	private Properties catmaProperties;

	private IDGenerator idGenerator;

	public Neo4JTagsetTest() throws Exception {
		String propertiesFile = System.getProperties().containsKey("prop") ?
				System.getProperties().getProperty("prop") : "catma.properties";

		this.catmaProperties = new Properties();
		this.catmaProperties.load(new FileInputStream(propertiesFile));

		this.idGenerator = new IDGenerator();
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
		try (Neo4JOGMSessionFactory neo4JOGMSessionFactory = new Neo4JOGMSessionFactory(this.catmaProperties)) {
			Session session = neo4JOGMSessionFactory.getSession();

			// TODO: delete exactly the graph created by the test so that it's safe to run this even against the
			// production DB
//			session.query("MATCH (n) DETACH DELETE n", new HashMap<>());
		}
	}

	public static PropertyDefinition getFakePropertyDefinition(String name, boolean singleSelect, String... values){
		String uuid = new IDGenerator().generate();

		PropertyPossibleValueList userPropertyPossibleValues = new PropertyPossibleValueList(
				Arrays.asList(values), singleSelect
		);

		return new PropertyDefinition(null, uuid, name, userPropertyPossibleValues);
	}

	@Test
	public void insertTagsetWithTagDefinitions() throws Exception {
		try (Neo4JOGMSessionFactory neo4JOGMSessionFactory = new Neo4JOGMSessionFactory(this.catmaProperties)) {

			String tagsetDefinitionUuid = this.idGenerator.generate();

			TagsetDefinition tagsetDefinition = new TagsetDefinition();
			tagsetDefinition.setUuid(tagsetDefinitionUuid);
			tagsetDefinition.setName("ALovelyName");
			tagsetDefinition.setRevisionHash("ABC123XYZ");

			TagDefinition tagDefinition1 = new TagDefinition();
			tagDefinition1.setUuid("CATMA_TagDefinition1");
			tagDefinition1.setName("TagDefinition1");

			tagDefinition1.addUserDefinedPropertyDefinition(
					Neo4JTagsetTest.getFakePropertyDefinition(
							"TagDefinition1PropDef1",
							true,
							"Verb", "Noun"
					));
			tagDefinition1.addUserDefinedPropertyDefinition(
					Neo4JTagsetTest.getFakePropertyDefinition(
							"TagDefinition1PropDef2",
							true,
							"Monday", "Friday"
					));
			tagDefinition1.addSystemPropertyDefinition(
					Neo4JTagsetTest.getFakePropertyDefinition(
							PropertyDefinition.SystemPropertyName.catma_markupauthor.toString(),
							true,
							"Frank", "Joe"
					));

			TagDefinition tagDefinition2 = new TagDefinition();
			tagDefinition2.setUuid("CATMA_TagDefinition2");
			tagDefinition2.setParentUuid("CATMA_TagDefinition1");
			tagDefinition2.setName("TagDefinition2");

			TagDefinition tagDefinition3 = new TagDefinition();
			tagDefinition3.setUuid("CATMA_TagDefinition3");
			tagDefinition3.setParentUuid("CATMA_TagDefinition2");
			tagDefinition3.setName("TagDefinition3");

			tagsetDefinition.addTagDefinition(tagDefinition1);
			tagsetDefinition.addTagDefinition(tagDefinition2);
			tagsetDefinition.addTagDefinition(tagDefinition3);

			Neo4JTagset neo4JTagset = new Neo4JTagset(tagsetDefinition);

			org.neo4j.ogm.session.Session session = neo4JOGMSessionFactory.getSession();

			session.save(neo4JTagset);

			session = neo4JOGMSessionFactory.getSession();

			Neo4JTagset loaded = session.load(
					Neo4JTagset.class, neo4JTagset.getId(), 3
			);
			TagsetDefinition loadedTagsetDefinition = loaded.getTagsetDefinition();

			assertEquals(tagsetDefinitionUuid, loadedTagsetDefinition.getUuid());
			assertEquals("ALovelyName", loadedTagsetDefinition.getName());

			assertTrue(loadedTagsetDefinition.hasTagDefinition(tagDefinition1.getUuid()));
			assertTrue(loadedTagsetDefinition.hasTagDefinition(tagDefinition2.getUuid()));
			assertTrue(loadedTagsetDefinition.hasTagDefinition(tagDefinition3.getUuid()));

			TagDefinition loadedTagDefinition1 = loadedTagsetDefinition.getTagDefinition(tagDefinition1.getUuid());

			assertEquals(1, loadedTagDefinition1.getSystemPropertyDefinitions().size());
			assertEquals(2, loadedTagDefinition1.getUserDefinedPropertyDefinitions().size());
		}
	}
}