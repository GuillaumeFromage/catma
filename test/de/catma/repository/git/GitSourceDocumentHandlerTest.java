package de.catma.repository.git;

import de.catma.document.source.*;
import de.catma.repository.git.exceptions.GitSourceDocumentHandlerException;
import de.catma.repository.git.managers.JGitRepoManager;
import de.catma.repository.git.managers.GitLabServerManager;
import de.catma.repository.git.managers.GitLabServerManagerTest;
import de.catma.repository.git.serialization.models.json_ld.JsonLdWebAnnotationTest;
import helpers.Randomizer;
import org.apache.commons.io.FileUtils;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

public class GitSourceDocumentHandlerTest {
	private Properties catmaProperties;
	private de.catma.user.User catmaUser;
	private GitLabServerManager gitLabServerManager;

	private ArrayList<File> directoriesToDeleteOnTearDown = new ArrayList<>();
	private ArrayList<String> sourceDocumentReposToDeleteOnTearDown = new ArrayList<>();
	private ArrayList<String> projectsToDeleteOnTearDown = new ArrayList<>();

    public GitSourceDocumentHandlerTest() throws Exception {
		String propertiesFile = System.getProperties().containsKey("prop") ?
				System.getProperties().getProperty("prop") : "catma.properties";

		this.catmaProperties = new Properties();
		this.catmaProperties.load(new FileInputStream(propertiesFile));
    }

    @Before
	public void setUp() throws Exception {
		// create a fake CATMA user which we'll use to instantiate the GitLabServerManager & JGitRepoManager
		this.catmaUser = Randomizer.getDbUser();

		this.gitLabServerManager = new GitLabServerManager(this.catmaProperties, catmaUser);
		this.gitLabServerManager.replaceGitLabServerUrl = true;
	}

	@After
	public void tearDown() throws Exception {
		if (this.directoriesToDeleteOnTearDown.size() > 0) {
			for (File dir : this.directoriesToDeleteOnTearDown) {
				FileUtils.deleteDirectory(dir);
			}
			this.directoriesToDeleteOnTearDown.clear();
		}

		if (this.sourceDocumentReposToDeleteOnTearDown.size() > 0) {
			for (String sourceDocumentId : this.sourceDocumentReposToDeleteOnTearDown) {
				List<Project> projects = this.gitLabServerManager.getAdminGitLabApi().getProjectApi().getProjects(
					sourceDocumentId
				); // this getProjects overload does a search
				for (Project project : projects) {
					this.gitLabServerManager.deleteRepository(project.getId());
				}
				await().until(
					() -> this.gitLabServerManager.getAdminGitLabApi().getProjectApi().getProjects().isEmpty()
				);
			}
			this.sourceDocumentReposToDeleteOnTearDown.clear();
		}

		if (this.projectsToDeleteOnTearDown.size() > 0) {
			try (JGitRepoManager jGitRepoManager = new JGitRepoManager(this.catmaProperties, this.catmaUser)) {
				GitProjectHandler gitProjectHandler = new GitProjectHandler(jGitRepoManager, this.gitLabServerManager);

				for (String projectId : this.projectsToDeleteOnTearDown) {
					gitProjectHandler.delete(projectId);
				}
				this.projectsToDeleteOnTearDown.clear();
			}
		}

		// delete the GitLab user that the GitLabServerManager constructor in setUp would have
		// created - see GitLabServerManagerTest tearDown() for more info
		User user = this.gitLabServerManager.getGitLabUser();
		this.gitLabServerManager.getAdminGitLabApi().getUserApi().deleteUser(user.getId());
		GitLabServerManagerTest.awaitUserDeleted(
			this.gitLabServerManager.getAdminGitLabApi().getUserApi(), user.getId()
		);
	}

	@Test
	public void create() throws Exception {
		File originalSourceDocument = new File("testdocs/rose_for_emily.pdf");
		File convertedSourceDocument = new File("testdocs/rose_for_emily.txt");

		FileInputStream originalSourceDocumentStream = new FileInputStream(originalSourceDocument);
		FileInputStream convertedSourceDocumentStream = new FileInputStream(
			convertedSourceDocument
		);

		IndexInfoSet indexInfoSet = new IndexInfoSet();
		indexInfoSet.setLocale(Locale.ENGLISH);

		ContentInfoSet contentInfoSet = new ContentInfoSet(
			"William Faulkner",
			"",
			"",
			"A Rose for Emily"
		);

		TechInfoSet techInfoSet = new TechInfoSet(
			FileType.TEXT,
			StandardCharsets.UTF_8,
			FileOSType.DOS,
			705211438L,
			null
		);

		SourceDocumentInfo sourceDocumentInfo = new SourceDocumentInfo(
			indexInfoSet, contentInfoSet, techInfoSet
		);

		try (JGitRepoManager jGitRepoManager = new JGitRepoManager(this.catmaProperties, this.catmaUser)) {
			this.directoriesToDeleteOnTearDown.add(jGitRepoManager.getRepositoryBasePath());

			GitSourceDocumentHandler gitSourceDocumentHandler = new GitSourceDocumentHandler(
				jGitRepoManager, this.gitLabServerManager
			);

			GitProjectHandler gitProjectHandler = new GitProjectHandler(
				jGitRepoManager, this.gitLabServerManager
			);

			String projectId = gitProjectHandler.create(
				"Test CATMA Project", "This is a test CATMA project"
			);
			this.projectsToDeleteOnTearDown.add(projectId);

			// the JGitRepoManager instance should always be in a detached state after GitProjectHandler calls
			// return
			assertFalse(jGitRepoManager.isAttached());

			String sourceDocumentId = gitSourceDocumentHandler.create(
					projectId, null,
					originalSourceDocumentStream, originalSourceDocument.getName(),
					convertedSourceDocumentStream, convertedSourceDocument.getName(),
					sourceDocumentInfo
			);
			// we don't add the sourceDocumentId to this.sourceDocumentReposToDeleteOnTearDown as deletion of the
			// project will take care of that for us

			assertNotNull(sourceDocumentId);
			assert sourceDocumentId.startsWith("CATMA_");

			// the JGitRepoManager instance should always be in a detached state after GitSourceDocumentHandler
			// calls return
			assertFalse(jGitRepoManager.isAttached());

			File expectedRepoPath = new File(
				jGitRepoManager.getRepositoryBasePath(),
				GitSourceDocumentHandler.getSourceDocumentRepositoryName(sourceDocumentId)
			);

			assert expectedRepoPath.exists();
			assert expectedRepoPath.isDirectory();
			assert Arrays.asList(expectedRepoPath.list()).contains("rose_for_emily.pdf");
			assert Arrays.asList(expectedRepoPath.list()).contains("rose_for_emily.txt");
			assert FileUtils.contentEquals(
				originalSourceDocument, new File(expectedRepoPath, "rose_for_emily.pdf")
			);
			assert FileUtils.contentEquals(
				convertedSourceDocument, new File(expectedRepoPath, "rose_for_emily.txt")
			);

			assert Arrays.asList(expectedRepoPath.list()).contains("header.json");

			String expectedSerializedSourceDocumentInfo = "" +
					"{\n" +
					"\t\"gitContentInfoSet\":{\n" +
					"\t\t\"author\":\"William Faulkner\",\n" +
					"\t\t\"description\":\"\",\n" +
					"\t\t\"publisher\":\"\",\n" +
					"\t\t\"title\":\"A Rose for Emily\"\n" +
					"\t},\n" +
					"\t\"gitIndexInfoSet\":{\n" +
					"\t\t\"locale\":\"en\",\n" +
					"\t\t\"unseparableCharacterSequences\":[],\n" +
					"\t\t\"userDefinedSeparatingCharacters\":[]\n" +
					"\t},\n" +
					"\t\"gitTechInfoSet\":{\n" +
					"\t\t\"charset\":\"UTF-8\",\n" +
					"\t\t\"checksum\":705211438,\n" +
					"\t\t\"fileName\":null,\n" +
					"\t\t\"fileOSType\":\"DOS\",\n" +
					"\t\t\"fileType\":\"TEXT\",\n" +
					"\t\t\"mimeType\":null,\n" +
					"\t\t\"uRI\":null,\n" +
					"\t\t\"xsltDocumentLocalUri\":null\n" +
					"\t}\n" +
					"}";

			assertEquals(
				expectedSerializedSourceDocumentInfo.replaceAll("[\n\t]", ""),
				FileUtils.readFileToString(new File(expectedRepoPath, "header.json"), StandardCharsets.UTF_8)
			);
		}
	}

	// how to test for exceptions: https://stackoverflow.com/a/31826781
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void delete() throws Exception {
		try (JGitRepoManager jGitRepoManager = new JGitRepoManager(this.catmaProperties, this.catmaUser)) {
			GitSourceDocumentHandler gitSourceDocumentHandler = new GitSourceDocumentHandler(
				jGitRepoManager, this.gitLabServerManager
			);

			thrown.expect(GitSourceDocumentHandlerException.class);
			thrown.expectMessage("Not implemented");
			gitSourceDocumentHandler.delete("fakeProjectId", "fakeSourceDocumentId");
		}
	}

	@Test
	public void open() throws Exception {
		try (JGitRepoManager jGitRepoManager = new JGitRepoManager(this.catmaProperties, this.catmaUser)) {
			this.directoriesToDeleteOnTearDown.add(jGitRepoManager.getRepositoryBasePath());

			HashMap<String, Object> getJsonLdWebAnnotationResult = JsonLdWebAnnotationTest.getJsonLdWebAnnotation(
					jGitRepoManager, this.gitLabServerManager
			);

			String projectId = (String)getJsonLdWebAnnotationResult.get("projectUuid");
			String sourceDocumentId = (String)getJsonLdWebAnnotationResult.get("sourceDocumentUuid");

			this.projectsToDeleteOnTearDown.add(projectId);

			GitSourceDocumentHandler gitSourceDocumentHandler = new GitSourceDocumentHandler(
					jGitRepoManager, this.gitLabServerManager
			);

			SourceDocument loadedSourceDocument = gitSourceDocumentHandler.open(projectId, sourceDocumentId);

			assertNotNull(loadedSourceDocument);
			assertEquals(
					"William Faulkner",
					loadedSourceDocument.getSourceContentHandler().getSourceDocumentInfo().getContentInfoSet()
							.getAuthor()
			);
			assertEquals(
					"A Rose for Emily",
					loadedSourceDocument.getSourceContentHandler().getSourceDocumentInfo().getContentInfoSet()
							.getTitle()
			);
			assertNotNull(loadedSourceDocument.getRevisionHash());
		}
	}
}