package de.catma.repository.git.interfaces;

import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollection;
import de.catma.repository.git.exceptions.MarkupCollectionHandlerException;
import de.catma.repository.git.serialization.models.json_ld.JsonLdWebAnnotation;

import javax.annotation.Nullable;

public interface IMarkupCollectionHandler {
	String create(String name, String description, String sourceDocumentId, String sourceDocumentVersion,
				  String projectId, @Nullable String markupCollectionId)
			throws MarkupCollectionHandlerException;

	void delete(String markupCollectionId) throws MarkupCollectionHandlerException;

	void addTagset(String markupCollectionId, String tagsetId, String tagsetVersion)
			throws MarkupCollectionHandlerException;

	void removeTagset(String markupCollectionId, String tagsetId) throws MarkupCollectionHandlerException;

	void addTagInstance(String markupCollectionId, JsonLdWebAnnotation annotation)
			throws MarkupCollectionHandlerException;

	UserMarkupCollection open(String projectId, String markupCollectionId) throws MarkupCollectionHandlerException;

}
