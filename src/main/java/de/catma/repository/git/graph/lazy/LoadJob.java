package de.catma.repository.git.graph.lazy;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.google.common.collect.Maps;

import de.catma.backgroundservice.DefaultProgressCallable;
import de.catma.document.annotation.AnnotationCollection;
import de.catma.document.annotation.AnnotationCollectionReference;
import de.catma.document.source.SourceDocument;
import de.catma.document.source.SourceDocumentReference;
import de.catma.project.ProjectReference;
import de.catma.repository.git.graph.interfaces.CollectionsSupplier;
import de.catma.repository.git.graph.interfaces.DocumentFileURIProvider;
import de.catma.tag.TagManager;
import de.catma.tag.TagsetDefinition;
import de.catma.util.Pair;

class LoadJob extends DefaultProgressCallable<Pair<TagManager,  Map<String, SourceDocumentReference>>> {

	private final ProjectReference projectReference;
	private String revisionHash;
	private final TagManager tagManager;
	private final Supplier<List<TagsetDefinition>> tagsetsSupplier;
	private final Supplier<List<SourceDocument>> documentsSupplier;
	private final CollectionsSupplier collectionsSupplier;
	private DocumentFileURIProvider fileInfoProvider;
	
	public LoadJob(ProjectReference projectReference, String revisionHash, TagManager tagManager,
			Supplier<List<TagsetDefinition>> tagsetsSupplier, Supplier<List<SourceDocument>> documentsSupplier,
			CollectionsSupplier collectionsSupplier, DocumentFileURIProvider fileInfoProvider) {
		super();
		this.projectReference = projectReference;
		this.revisionHash = revisionHash;
		this.tagManager = tagManager;
		this.tagsetsSupplier = tagsetsSupplier;
		this.documentsSupplier = documentsSupplier;
		this.collectionsSupplier = collectionsSupplier;
		this.fileInfoProvider = fileInfoProvider;
	}


	@Override
	public Pair<TagManager, Map<String, SourceDocumentReference>> call() throws Exception {
		
		Map<String, SourceDocumentReference> docRefsById = Maps.newHashMap();
		
		getProgressListener().setProgress("Loading Tagsets for Project %1$s", projectReference);
		this.tagManager.load(tagsetsSupplier.get());

		getProgressListener().setProgress(String.format("Loading Documents for Project %1$s", projectReference));
		for (SourceDocument document : documentsSupplier.get()) {
			document.getSourceContentHandler().getSourceDocumentInfo().getTechInfoSet().setURI(
					fileInfoProvider.getSourceDocumentFileURI(document.getUuid()));
			docRefsById.put(document.getUuid(), new SourceDocumentReference(document.getUuid(), document.getSourceContentHandler()));
		}
		
		getProgressListener().setProgress(String.format("Loading Collections for Project %1$s", projectReference));
		for (AnnotationCollection collection : collectionsSupplier.get(this.tagManager.getTagLibrary())) {
			docRefsById.get(collection.getSourceDocumentId()).addUserMarkupCollectionReference(new AnnotationCollectionReference(collection));
			
		}
		
		Logger.getLogger(getClass().getName()).info(String.format(
				"Finished loading Collections for Project %1$s", projectReference));
		return new Pair<>(this.tagManager, docRefsById);
	}
}
