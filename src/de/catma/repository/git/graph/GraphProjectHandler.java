package de.catma.repository.git.graph;

import static de.catma.repository.git.graph.NodeType.MarkupCollection;
import static de.catma.repository.git.graph.NodeType.Project;
import static de.catma.repository.git.graph.NodeType.ProjectRevision;
import static de.catma.repository.git.graph.NodeType.SourceDocument;
import static de.catma.repository.git.graph.NodeType.Tag;
import static de.catma.repository.git.graph.NodeType.Tagset;
import static de.catma.repository.git.graph.NodeType.User;
import static de.catma.repository.git.graph.NodeType.nt;
import static de.catma.repository.git.graph.RelationType.hasCollection;
import static de.catma.repository.git.graph.RelationType.hasDocument;
import static de.catma.repository.git.graph.RelationType.hasParent;
import static de.catma.repository.git.graph.RelationType.hasProject;
import static de.catma.repository.git.graph.RelationType.hasRevision;
import static de.catma.repository.git.graph.RelationType.hasTag;
import static de.catma.repository.git.graph.RelationType.hasTagset;
import static de.catma.repository.git.graph.RelationType.rt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.neo4j.driver.internal.value.NullValue;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.SchedulerRepository;

import com.google.common.collect.Lists;

import de.catma.document.source.ContentInfoSet;
import de.catma.document.source.FileOSType;
import de.catma.document.source.FileType;
import de.catma.document.source.IndexInfoSet;
import de.catma.document.source.SourceDocument;
import de.catma.document.source.SourceDocumentInfo;
import de.catma.document.source.TechInfoSet;
import de.catma.document.source.contenthandler.SourceContentHandler;
import de.catma.document.source.contenthandler.StandardContentHandler;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollectionReference;
import de.catma.project.ProjectReference;
import de.catma.repository.git.graph.indexer.SourceDocumentIndexerJob;
import de.catma.repository.git.graph.indexer.SourceDocumentIndexerJob.DataField;
import de.catma.repository.neo4j.SessionRunner;
import de.catma.repository.neo4j.StatementExcutor;
import de.catma.repository.neo4j.ValueContainer;
import de.catma.tag.TagDefinition;
import de.catma.tag.TagsetDefinition;
import de.catma.tag.Version;
import de.catma.user.User;
import de.catma.util.ColorConverter;

public class GraphProjectHandler {
	private Logger logger = Logger.getLogger(GraphProjectHandler.class.getName());
	private User user;
	private ProjectReference projectReference;
	private FileInfoProvider fileInfoProvider;
	
	public GraphProjectHandler(
			ProjectReference projectReference, 
			User user, FileInfoProvider fileInfoProvider) {
		this.projectReference = projectReference;
		this.user = user;
		this.fileInfoProvider = fileInfoProvider;
	}

	public void ensureProjectRevisionIsLoaded(
			String revisionHash, 
			Supplier<Stream<SourceDocument>> sourceDocumentsSupplier,
			Supplier<Stream<UserMarkupCollectionReference>> collectionsSupplier) throws Exception {
		ValueContainer<Boolean> revisionExists = new ValueContainer<>();
		StatementExcutor.execute(new SessionRunner() {
			@Override
			public void run(Session session) throws Exception {
				StatementResult statementResult = session.run(
					"MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
					+"(p:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]-> "
					+ "(:"+nt(ProjectRevision)+"{revisionHash:{pRevisionHash}}) "
					+ " RETURN p.projectId ", 
					Values.parameters(
						"pUserId", user.getIdentifier(),
						"pProjectId", projectReference.getProjectId(),
						"pRevisionHash", revisionHash
					)
				);
				
				revisionExists.setValue(statementResult.hasNext());
			}
		});
		
		if (!revisionExists.getValue()) {
			//TODO: implement load
			
			StatementExcutor.execute(new SessionRunner() {
				@Override
				public void run(Session session) throws Exception {

					session.run(
						"MERGE (u:"+nt(User)+"{userId:{pUserId}})"
						+ "MERGE (p:"+nt(Project)+"{projectId:{pProjectId}})"
						+ "MERGE (u)-[:"+rt(hasProject)+"]->(p)"
						+ " MERGE (p) "
						+ " -[:"+rt(hasRevision)+"]-> "
						+ "(pr:"+nt(ProjectRevision)+"{revisionHash:{pRevisionHash}}) ", 
						Values.parameters(
							"pUserId", user.getIdentifier(),
							"pProjectId", projectReference.getProjectId(),
							"pRevisionHash", revisionHash
						)
					);
					
					sourceDocumentsSupplier.get().forEach(sourceDocument -> {
						SourceDocumentInfo info = 
							sourceDocument.getSourceContentHandler().getSourceDocumentInfo();
						session.run(
							" MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
							+"(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
							+ "(pr:"+nt(ProjectRevision)+"{revisionHash:{pRevisionHash}}) "
							+ " MERGE (pr)-[:"+rt(hasDocument)+"]->"
							+ "(:"+nt(SourceDocument)+"{"
							+ "sourceDocumentId:{pSourceDocumentId}, "
							+ "revisionHash:{pSourceDocumentRevisionHash}, "
							+ "locale:{pLocale}, "
							+ "publisher:{pPublisher}, "
							+ "author:{pAuthor}, "
							+ "description:{pDescription},"
							+ "title:{pTitle}, "
							+ "checksum:{pChecksum}, "
							+ "fileOSType:{pFileOSType}, "
							+ "indexCompleted:{pIndexCompleted}"
							+ "}) ",
							Values.parameters(
								"pUserId", user.getIdentifier(),
								"pProjectId", projectReference.getProjectId(),
								"pRevisionHash", revisionHash,
								"pSourceDocumentId", sourceDocument.getID(),
								"pSourceDocumentRevisionHash", sourceDocument.getRevisionHash(),
								"pLocale", info.getIndexInfoSet().getLocale().toString(),
								"pPublisher", info.getContentInfoSet().getPublisher(),
								"pAuthor", info.getContentInfoSet().getAuthor(),
								"pDescription", info.getContentInfoSet().getDescription(),
								"pTitle", info.getContentInfoSet().getTitle(),
								"pChecksum", info.getTechInfoSet().getChecksum(),
								"pFileOSType", info.getTechInfoSet().getFileOSType().name(),
								"pIndexCompleted", false
							)
						);
//						try {
//							scheduleSourceDocumentForIndexing(
//									revisionHash, 
//									sourceDocument, 
//									fileInfoProvider.getTokenizedSourceDocumentPath(sourceDocument.getID()));
//						} catch (Exception e) {
//							throw new RuntimeException(e);
//						}
					});
					
					collectionsSupplier.get().forEach(collectionReference -> {
						
						session.run(
							"MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
							+"(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
							+ "(pr:"+nt(ProjectRevision)+"{revisionHash:{pRevisionHash}})-[:"+rt(hasDocument)+"]->"
							+ "(s:"+nt(SourceDocument)+"{sourceDocumentId:{pSourceDocumentId}}) "
							+ "MERGE (s)-[:"+rt(hasCollection)+"]->"
							+ "(:"+nt(MarkupCollection)+"{"
							+ "collectionId:{pCollectionId},"
							+ "revisionHash:{pUmcRevisionHash},"
							+ "name:{pName}"
							+ "})",
							Values.parameters(
								"pUserId", user.getIdentifier(),
								"pProjectId", projectReference.getProjectId(),
								"pRevisionHash", revisionHash,
								"pSourceDocumentId", collectionReference.getSourceDocumentId(),
								"pCollectionId", collectionReference.getId(),
								"pUmcRevisionHash", collectionReference.getRevisionHash(),
								"pName", collectionReference.getName()
							)
						);
					});
				}
			});
			
		}
		
	}

	public void insertSourceDocument(
			String oldRootRevisionHash, String rootRevisionHash, SourceDocument sourceDocument, 
			Path tokenizedSourceDocumentPath) throws Exception {
		
		StatementExcutor.execute(new SessionRunner() {
			@Override
			public void run(Session session) throws Exception {
				SourceDocumentInfo info = sourceDocument.getSourceContentHandler().getSourceDocumentInfo();
				
				session.run(
					" MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
						+"(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
						+ "(pr:"+nt(ProjectRevision)+"{revisionHash:{pOldRootRevisionHash}}) "
					+ " SET pr.revisionHash = {pRootRevisionHash}"
					+ " WITH pr "
					+ " MERGE (pr)-[:"+rt(hasDocument)+"]->"
							+ "(:"+nt(SourceDocument)+"{"
								+ "sourceDocumentId:{pSourceDocumentId}, "
								+ "revisionHash:{pSourceDocumentRevisionHash}, "
								+ "locale:{pLocale}, "
								+ "publisher:{pPublisher}, "
								+ "author:{pAuthor}, "
								+ "description:{pDescription},"
								+ "title:{pTitle}, "
								+ "checksum:{pChecksum}, "
								+ "fileOSType:{pFileOSType}, "
								+ "indexCompleted:{pIndexCompleted}"
							+ "}) ",
					Values.parameters(
						"pUserId", user.getIdentifier(),
						"pProjectId", projectReference.getProjectId(),
						"pOldRootRevisionHash", oldRootRevisionHash,
						"pRootRevisionHash", rootRevisionHash,
						"pSourceDocumentId", sourceDocument.getID(),
						"pSourceDocumentRevisionHash", sourceDocument.getRevisionHash(),
						"pLocale", info.getIndexInfoSet().getLocale().toString(),
						"pPublisher", info.getContentInfoSet().getPublisher(),
						"pAuthor", info.getContentInfoSet().getAuthor(),
						"pDescription", info.getContentInfoSet().getDescription(),
						"pTitle", info.getContentInfoSet().getTitle(),
						"pChecksum", info.getTechInfoSet().getChecksum(),
						"pFileOSType", info.getTechInfoSet().getFileOSType().name(),
						"pIndexCompleted", false
					)
				);

			}
			
		});

		scheduleSourceDocumentForIndexing(rootRevisionHash, sourceDocument, tokenizedSourceDocumentPath);
	}

	private void scheduleSourceDocumentForIndexing(
			String rootRevisionHash, SourceDocument sourceDocument, Path tokenizedSourceDocumentPath) throws Exception {
		String projectId = projectReference.getProjectId();
		
        SchedulerRepository schedRep = SchedulerRepository.getInstance();
    	
        Scheduler scheduler = schedRep.lookup("CATMAQuartzScheduler");
        
        JobDataMap jobDataMap = new JobDataMap();
        
        jobDataMap.put(
        	DataField.title.name(), 
        	sourceDocument.getSourceContentHandler().getSourceDocumentInfo().getContentInfoSet().getTitle());
        jobDataMap.put(DataField.userId.name(), user.getIdentifier());
        jobDataMap.put(DataField.projectId.name(), projectId);
		jobDataMap.put(DataField.rootRevisionHash.name(), rootRevisionHash);
		jobDataMap.put(DataField.sourceDocumentId.name(), sourceDocument.getID());
		jobDataMap.put(DataField.sourceDocumentRevisionHash.name(), sourceDocument.getRevisionHash());
		jobDataMap.put(DataField.tokenizedSourceDocumentPath.name(), tokenizedSourceDocumentPath.toString());

		String userName = user.getIdentifier();
		
    	JobDetail jobDetail = 
        	JobBuilder.newJob(SourceDocumentIndexerJob.class)
        	.withIdentity(
        			userName+"_" +projectId+"_"+rootRevisionHash+"_"+sourceDocument.getID()+"_"+sourceDocument.getRevisionHash(),
        			userName)
    		.setJobData(jobDataMap)
    		.build();
		
    	Trigger trigger = TriggerBuilder.newTrigger()
		    .withIdentity(userName+"_" +projectId+"_"+rootRevisionHash+"_"+sourceDocument.getID()+"_"+sourceDocument.getRevisionHash(), userName)
		    .startNow()
		    .build();
    	
    	scheduler.scheduleJob(jobDetail, trigger);
	}

	public Collection<SourceDocument> getSourceDocuments(String rootRevisionHash) throws Exception {
		ArrayList<SourceDocument> sourceDocuments = new ArrayList<>();
		
		
		StatementExcutor.execute(new SessionRunner() {
			@Override
			public void run(Session session) throws Exception {
				StatementResult statementResult = session.run(
					"MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"+""
					+ "(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
					+ "(:"+nt(ProjectRevision)+"{revisionHash:{pRootRevisionHash}})-[:"+rt(hasDocument)+"]->"
					+ "(s:"+nt(SourceDocument)+") "
					+ "RETURN s",
					Values.parameters(
						"pUserId", user.getIdentifier(),
						"pProjectId", projectReference.getProjectId(),
						"pRootRevisionHash", rootRevisionHash
					)
				);

				while (statementResult.hasNext()) {
					Record record = statementResult.next();
					sourceDocuments.add(createSourceDocument(record.get("s")));
				}
			}
			
		});

		return sourceDocuments;
	}

	private SourceDocument createSourceDocument(Value sourceDocumentValue) throws Exception {
		String sourceDocumentId = sourceDocumentValue.get("sourceDocumentId").asString();
		String locale = sourceDocumentValue.get("locale").asString();
		String author = sourceDocumentValue.get("author").asString();
		String description = sourceDocumentValue.get("description").asString();
		String publisher = sourceDocumentValue.get("publisher").asString();
		String title = sourceDocumentValue.get("title").asString();
		Long checksum = sourceDocumentValue.get("checksum").equals(NullValue.NULL)?null:sourceDocumentValue.get("checksum").asLong();
		FileOSType fileOSType = FileOSType.valueOf(sourceDocumentValue.get("fileOSType").asString());
		
		SourceContentHandler contentHandler = new StandardContentHandler();
		TechInfoSet techInfoSet =
				new TechInfoSet(
						FileType.TEXT, 
						StandardCharsets.UTF_8, 
						fileOSType, 
						checksum);
		
		techInfoSet.setURI(fileInfoProvider.getSourceDocumentFileURI(sourceDocumentId));

		SourceDocumentInfo sourceDocumentInfo = 
				new SourceDocumentInfo(
					new IndexInfoSet(
						Collections.emptyList(), //TODO
						Collections.emptyList(), //TODO
						Locale.forLanguageTag(locale)), 
					new ContentInfoSet(author, description, publisher, title), 
					techInfoSet);
		contentHandler.setSourceDocumentInfo(sourceDocumentInfo);
		
		return new SourceDocument(sourceDocumentId, contentHandler);
	}

	public SourceDocument getSourceDocument(String rootRevisionHash, String sourceDocumentId) throws Exception {
		ValueContainer<SourceDocument> sourceDocumentContainer = new ValueContainer<>();
		
		
		StatementExcutor.execute(new SessionRunner() {
			@Override
			public void run(Session session) throws Exception {
				StatementResult statementResult = session.run(
					"MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
					+"(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
					+ "(:"+nt(ProjectRevision)+"{revisionHash:{pRootRevisionHash}})-[:"+rt(hasDocument)+"]->"
					+ "(s:"+nt(SourceDocument)+"{sourceDocumentId:{pSourceDocumentId}}) "
					+ "RETURN s",
					Values.parameters(
						"pUserId", user.getIdentifier(),
						"pProjectId", projectReference.getProjectId(),
						"pRootRevisionHash", rootRevisionHash,
						"pSourceDocumentId", sourceDocumentId
					)
				);

				if (statementResult.hasNext()) {
					Record record = statementResult.next();
					sourceDocumentContainer.setValue(createSourceDocument(record.get("s")));
				}
			}
			
		});

		return sourceDocumentContainer.getValue();
	}

	public void createUserMarkupCollection(
		String rootRevisionHash,
		String collectionId, String name, String umcRevisionHash, SourceDocument sourceDocument, String oldRootRevisionHash) throws Exception {

		StatementExcutor.execute(new SessionRunner() {
			@Override
			public void run(Session session) throws Exception {
				session.run(
					"MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
					+"(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
					+ "(pr:"+nt(ProjectRevision)+"{revisionHash:{pOldRootRevisionHash}}) "
					+ "SET pr.revisionHash = {pRootRevisionHash} "
					+ "WITH pr "
					+ "MATCH (pr)-[:"+rt(hasDocument)+"]->"
					+ "(s:"+nt(SourceDocument)+"{sourceDocumentId:{pSourceDocumentId}}) "
					+ "MERGE (s)-[:"+rt(hasCollection)+"]->"
					+ "(:"+nt(MarkupCollection)+"{"
						+ "collectionId:{pCollectionId},"
						+ "revisionHash:{pUmcRevisionHash},"
						+ "name:{pName}"
					+ "})",
					Values.parameters(
						"pUserId", user.getIdentifier(),
						"pProjectId", projectReference.getProjectId(),
						"pOldRootRevisionHash", oldRootRevisionHash,
						"pRootRevisionHash", rootRevisionHash,
						"pSourceDocumentId", sourceDocument.getID(),
						"pCollectionId", collectionId,
						"pUmcRevisionHash", umcRevisionHash,
						"pName", name
					)
				);
			}
		});
	}

	public void createTagset(String rootRevisionHash, String tagsetId,
			String tagsetRevisionHash, String name, String description, String oldRootRevisionHash) throws Exception {
		
		StatementExcutor.execute(new SessionRunner() {
			@Override
			public void run(Session session) throws Exception {
				session.run(
					"MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
					+"(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
					+ "(pr:"+nt(ProjectRevision)+"{revisionHash:{pOldRootRevisionHash}}) "
					+ "SET pr.revisionHash = {pRootRevisionHash} "
					+ "WITH pr "
					+ "MERGE (pr)-[:"+rt(hasTagset)+"]->"
					+ "(:"+nt(Tagset)+"{"
						+ "tagsetId:{pTagsetId},"
						+ "revisionHash:{pTagsetRevisionHash},"
						+ "name:{pName},"
						+ "description:{pDescription}"
					+ "})",
					Values.parameters(
						"pUserId", user.getIdentifier(),
						"pProjectId", projectReference.getProjectId(),
						"pOldRootRevisionHash", oldRootRevisionHash,
						"pRootRevisionHash", rootRevisionHash,
						"pTagsetId", tagsetId,
						"pTagsetRevisionHash", tagsetRevisionHash,
						"pName", name,
						"pDescription", description
					)
				);
			}
		});
	}
	
	public void addTagDefinition(String rootRevisionHash, TagDefinition tagDefinition,
			TagsetDefinition tagsetDefinition) throws Exception {
		
		StatementExcutor.execute(new SessionRunner() {
			@Override
			public void run(Session session) throws Exception {
				session.run(
					"MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
					+"(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
					+ "(:"+nt(ProjectRevision)+"{revisionHash:{pRootRevisionHash}})-[:"+rt(hasTagset)+"]->"
					+ "(ts:"+nt(Tagset)+"{tagsetId:{pTagsetId}, revisionHash:{pTagsetRevisionHash}}) "
					+ "MERGE (ts)-[:"+rt(hasTag)+"]->"
					+ "(t:"+nt(Tag)+"{"
						+ "tagId:{pTagId},"
						+ "name:{pName},"
						+ "color:{pColor},"
//						+ "author:{pAuthor}," //TODO:
						+ "creationDate:{pCreationDate},"
						+ "modifiedDate:{pModifiedDate}"
					+ "}) "
					+ (tagDefinition.getParentUuid()==null?"": "WITH ts, t ")
					+ "MATCH (ts)-[:"+rt(hasTag)+"]->(parent:"+nt(Tag)+"{tagId:{parentTagId}}) "
					+ "MERGE (parent)-[:"+rt(hasParent)+"]->(t) ",
					Values.parameters(
						"pUserId", user.getIdentifier(),
						"pProjectId", projectReference.getProjectId(),
						"pRootRevisionHash", rootRevisionHash,
						"pTagsetId", tagsetDefinition.getUuid(),
						"pTagsetRevisionHash", tagsetDefinition.getRevisionHash(),
						"pTagId", tagDefinition.getUuid(),
						"pColor", ColorConverter.toHex(Integer.valueOf(tagDefinition.getColor())),
						"pName", tagDefinition.getName(),
						"pAuthor", tagDefinition.getAuthor(),
						"pCreationDate", ZonedDateTime.now().format(DateTimeFormatter.ofPattern(Version.DATETIMEPATTERN)),
						"pModifiedDate", tagDefinition.getVersion().toString(), //TODO: change to java.util.time
						"parentTagId", tagDefinition.getParentUuid()
					)
				);
			}
		});
		
	}

	public List<UserMarkupCollectionReference> getUserMarkupCollectionReferences(String rootRevisionHash, int offset, int limit) throws Exception {
		List<UserMarkupCollectionReference> collectionContainer = Lists.newArrayList();
		
		StatementExcutor.execute(new SessionRunner() {
			@Override
			public void run(Session session) throws Exception {
				StatementResult statementResult = session.run(
					"MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
					+"(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
					+ "(:"+nt(ProjectRevision)+"{revisionHash:{pRootRevisionHash}})-[:"+rt(hasDocument)+"]->"
					+ "(s:"+nt(SourceDocument)+")-[:"+rt(hasCollection)+"]->"
					+ "(c:"+nt(MarkupCollection)+") "
					+ "RETURN s.sourceDocumentId, s.title, c.collectionId, c.revisionHash, c.name SKIP {pOffset} LIMIT {pLimit} ",
					Values.parameters(
						"pUserId", user.getIdentifier(),
						"pProjectId", projectReference.getProjectId(),
						"pRootRevisionHash", rootRevisionHash,
						"pOffset", offset,
						"pLimit", limit
					)
				);
				
				while (statementResult.hasNext()) {
					Record record = statementResult.next();
					
					collectionContainer.add(
						createUserMarkupCollectionReference(
							record.get("c.collectionId"), 
							record.get("c.revisionHash"), 
							record.get("c.name"),
							record.get("s.sourceDocumentId"),
							record.get("s.title")));
				}
			}
		});
		
		
		return collectionContainer;
	}

	protected UserMarkupCollectionReference createUserMarkupCollectionReference(
		Value idValue, Value revisionHashValue, Value nameValue, 
		Value sourceDocumentIdValue, Value sourceDocumentTitleValue) {
		
		return new UserMarkupCollectionReference(
				idValue.asString(), 
				revisionHashValue.asString(), 
				new ContentInfoSet(nameValue.asString()),
				sourceDocumentIdValue.asString(),
				sourceDocumentTitleValue.asString());
	}

	public int getUserMarkupCollectionReferenceCount(String rootRevisionHash) throws Exception {
		ValueContainer<Integer> countContainer = new ValueContainer<>(0);
		
		StatementExcutor.execute(new SessionRunner() {
			@Override
			public void run(Session session) throws Exception {
				StatementResult statementResult = session.run(
					"MATCH (:"+nt(NodeType.User)+"{userId:{pUserId}})-[:"+rt(hasProject)+"]->"
					+"(:"+nt(Project)+"{projectId:{pProjectId}})-[:"+rt(hasRevision)+"]->"
					+ "(:"+nt(ProjectRevision)+"{revisionHash:{pRootRevisionHash}})-[:"+rt(hasDocument)+"]->"
					+ "(:"+nt(SourceDocument)+")-[:"+rt(hasCollection)+"]->"
					+ "(c:"+nt(MarkupCollection)+") "
					+ "RETURN count(c) as umcCount ",
					Values.parameters(
						"pUserId", user.getIdentifier(),
						"pProjectId", projectReference.getProjectId(),
						"pRootRevisionHash", rootRevisionHash
					)
				);
				
				if (statementResult.hasNext()) {
					countContainer.setValue(statementResult.next().get("umcCount").asInt());
				}
			}
		});

		return countContainer.getValue();
	}

	public Collection<TagsetDefinition> getTagsets() {


		
		return new ArrayList<>();
	}
}
