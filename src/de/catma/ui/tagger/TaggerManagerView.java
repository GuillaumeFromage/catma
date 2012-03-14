package de.catma.ui.tagger;

import java.util.Iterator;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TabSheet.CloseHandler;
import com.vaadin.ui.TabSheet.Tab;
import com.vaadin.ui.VerticalLayout;

import de.catma.core.document.source.SourceDocument;
import de.catma.core.document.standoffmarkup.usermarkup.UserMarkupCollection;
import de.catma.core.tag.TagManager;

public class TaggerManagerView extends VerticalLayout implements CloseHandler {
	
	private TabSheet tabSheet;
	private Label noOpenTaggers;
	
	public TaggerManagerView() {
		initComponents();
	}
	
	private void initComponents() {
		tabSheet = new TabSheet();
		noOpenTaggers = 
			new Label(
				"There are no open Taggers. " +
				"Please use the Repository Manager to open a Source Document in a Tagger.");
		
		tabSheet.setCloseHandler(this);
		
		noOpenTaggers.setSizeFull();
		setMargin(true);
		addComponent(noOpenTaggers);
		setComponentAlignment(noOpenTaggers, Alignment.MIDDLE_CENTER);
		
		tabSheet.setVisible(false);
		addComponent(tabSheet);
	}

	public TaggerView openSourceDocument(
			TagManager tagManager, SourceDocument sourceDocument) {
		TaggerView taggerView = getTaggerView(sourceDocument);
		if (taggerView != null) {
			tabSheet.setSelectedTab(taggerView);
		}
		else {
			taggerView = new TaggerView(tagManager, sourceDocument);
			Tab tab = tabSheet.addTab(taggerView, sourceDocument.toString());
			tab.setClosable(true);
			tabSheet.setSelectedTab(tab.getComponent());
		}
		
		if (!tabSheet.isVisible()) {
			noOpenTaggers.setVisible(false);
			setMargin(false);
			tabSheet.setVisible(true);
			tabSheet.setSizeFull();
		}
		
		return taggerView;
	}
	
	
	private TaggerView getTaggerView(SourceDocument sourceDocument) {
		Iterator<Component> iterator = tabSheet.getComponentIterator();
		while (iterator.hasNext()) {
			Component c = iterator.next();
			TaggerView taggerView = (TaggerView)c;
			if (taggerView.getSourceDocument().getID().equals(sourceDocument.getID())) {
				return taggerView;
			}
		}
		
		return null;
	}
	

	public void onTabClose(TabSheet tabsheet, Component tabContent) {
		// workaround for http://dev.vaadin.com/ticket/7686
		
		tabsheet.removeComponent(tabContent);
		try {
			Thread.sleep(5);
		} catch (InterruptedException ex) {
	            //do nothing 
	    }
		if (tabsheet.getComponentCount() == 0) {
			tabsheet.setVisible(false);
			noOpenTaggers.setVisible(true);
			setMargin(true);
		}

	}

	public void openUserMarkupCollection(TaggerView taggerView,
			UserMarkupCollection userMarkupCollection) {
		taggerView.openUserMarkupCollection(userMarkupCollection);
	}
}