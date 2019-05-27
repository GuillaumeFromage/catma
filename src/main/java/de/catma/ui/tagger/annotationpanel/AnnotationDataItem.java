package de.catma.ui.tagger.annotationpanel;

import com.vaadin.icons.VaadinIcons;

import de.catma.document.standoffmarkup.usermarkup.Annotation;
import de.catma.indexer.KwicProvider;
import de.catma.tag.TagDefinition;
import de.catma.tag.TagsetDefinition;

public class AnnotationDataItem implements AnnotationTreeItem {
	private Annotation annotation;
	private String annotatedText;
	private String keywordInContext;
	private KwicProvider kwicProvider;
	private TagsetDefinition tagset;
	
	public AnnotationDataItem(Annotation annotation,  TagsetDefinition tagset, KwicProvider kwicProvider) {
		super();
		this.annotation = annotation;
		this.kwicProvider = kwicProvider;
		this.tagset = tagset;
		TagDefinition tagDefinition = 
			tagset.getTagDefinition(annotation.getTagInstance().getTagDefinitionId());
		this.annotatedText = AnnotatedTextProvider.buildAnnotatedText(
				annotation.getTagReferences(), 
				kwicProvider, 
				tagDefinition);
		this.keywordInContext = AnnotatedTextProvider.buildKeywordInContext(
			annotation.getTagReferences(), kwicProvider, tagDefinition, tagset.getTagPath(tagDefinition));
	}
	
	@Override
	public String getDetail() {
		return annotatedText;
	}

	@Override
	public String getTag() {
		return tagset.getTagDefinition(annotation.getTagInstance().getTagDefinitionId()).getName();
	}

	@Override
	public String getAuthor() {
		return annotation.getTagInstance().getAuthor();
	}

	@Override
	public String getCollection() {
		return annotation.getUserMarkupCollection().getName();
	}

	@Override
	public String getTagset() {
		return tagset.getName();
	}

	@Override
	public String getAnnotationId() {
		return annotation.getTagInstance().getUuid();
	}
	
	@Override
	public String getDescription() {
		return keywordInContext;
	}

	@Override
	public String getEditIcon() {
		return VaadinIcons.EDIT.getHtml();
	}
	
	@Override
	public String getDeleteIcon() {
		return VaadinIcons.TRASH.getHtml();
	}
	
	public Annotation getAnnotation() {
		return annotation;
	}
	
}