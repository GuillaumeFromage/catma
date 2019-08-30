package de.catma.ui.analyzenew;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import de.catma.document.Corpus;

public class AnalyzeCaption {
	
	private LocalDateTime timestamp;
	private String caption;
	private boolean setManually = false;

	public AnalyzeCaption(Corpus corpus) {
		this.timestamp = LocalDateTime.now();
		this.caption = createDefault(corpus);
	}

	private String createDefault(Corpus corpus) {
		return String.format("%1$d Document(s), %2$d Collection(s) %3$s", 
				corpus.getSourceDocuments().size(), 
				corpus.getUserMarkupCollectionRefs().size(),
				timestamp.format(DateTimeFormatter.ISO_LOCAL_TIME));
	}

	public void setCaption(String caption) {
		this.caption = caption;
		setManually = true;
	}
	
	public String setCaption(Corpus corpus) {
		if (!setManually) {
			this.caption = createDefault(corpus);
		}
		return caption;
	}
	
	public boolean isSetManually() {
		return setManually;
	}
	
	public String getCaption() {
		return caption;
	}
}
