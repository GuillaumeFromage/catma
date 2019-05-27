/*   
 *   CATMA Computer Aided Text Markup and Analysis
 *   
 *   Copyright (C) 2009-2013  University Of Hamburg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.catma.ui.tagger;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.vaadin.ui.Component;

import de.catma.document.repository.Repository;
import de.catma.document.repository.event.ChangeType;
import de.catma.document.repository.event.DocumentChangeEvent;
import de.catma.document.source.SourceDocument;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollection;
import de.catma.ui.events.TaggerViewSourceDocumentChangedEvent;
import de.catma.ui.tabbedview.TabbedView;

public class TaggerManagerView extends TabbedView {
	
	private int nextTaggerID = 1;
	private final EventBus eventBus;
	
	public TaggerManagerView(EventBus eventBus, Repository project) {
		super(() -> new TaggerView(
				0, null, project,
				eventBus));
		this.eventBus = eventBus;
		this.eventBus.register(this);
	}
	
	@Subscribe
	public void taggerViewSourceDocumentChanged(
			TaggerViewSourceDocumentChangedEvent taggerViewSourceDocumentChangedEvent) {
		TaggerView taggerView = taggerViewSourceDocumentChangedEvent.getTaggerView();
		String caption = taggerView.getSourceDocument()==null?"N/A":taggerView.getSourceDocument().toString();
		setCaption(taggerView, caption);
	}
	
    @Subscribe
    public void handleDocumentChanged(DocumentChangeEvent documentChangeEvent) {

		if (documentChangeEvent.getChangeType().equals(ChangeType.DELETED)) {
				TaggerView taggerView = 
						getTaggerView(documentChangeEvent.getDocument());
			if (taggerView != null) {
				onTabClose(taggerView);
			}
		}
		else if (documentChangeEvent.getChangeType().equals(ChangeType.UPDATED)) {
			SourceDocument document = documentChangeEvent.getDocument();
			TaggerView taggerView = getTaggerView(document);
			if (taggerView != null) {
				taggerView.setSourceDocument(document);
			}
		}    
    }
    
	public TaggerView openSourceDocument(
			final SourceDocument sourceDocument, Repository repository) {

		TaggerView taggerView = getTaggerView(sourceDocument);
		if (taggerView != null) {
			setSelectedTab(taggerView);
		}
		else {
			taggerView = new TaggerView(
					nextTaggerID++, sourceDocument, repository,
					eventBus);
			addClosableTab(taggerView, sourceDocument.toString());
			setSelectedTab(taggerView);
		}
		
		return taggerView;
	}
	
	
	private TaggerView getTaggerView(SourceDocument sourceDocument) {
		for (Component tabContent : this.getTabSheet()) {
			TaggerView taggerView = (TaggerView)tabContent;
			if (taggerView.getSourceDocument().getID().equals(
					sourceDocument.getID())) {
				return taggerView;
			}
		}
		
		return null;
	}

	public void openUserMarkupCollection(TaggerView taggerView,
			UserMarkupCollection userMarkupCollection) {
		taggerView.openUserMarkupCollection(userMarkupCollection);
	}
	
	@Override
	public void closeClosables() {
		eventBus.unregister(this);
		super.closeClosables();
	}
}
