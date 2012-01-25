package de.catma.ui.tagmanager;

import java.util.Iterator;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TabSheet.CloseHandler;
import com.vaadin.ui.TabSheet.Tab;
import com.vaadin.ui.VerticalLayout;

import de.catma.core.tag.TagLibrary;

public class TagManagerView extends VerticalLayout implements CloseHandler {
	
	private TabSheet tabSheet;
	private Label noOpenTagLibraries;
	
	public TagManagerView() {
		tabSheet = new TabSheet();
		noOpenTagLibraries = 
			new Label(
				"There are no open Tag Libraries. " +
				"Please use the Repository Manager to open a Tag Libray.");
		
		tabSheet.setCloseHandler(this);
		setEmptyLabel();
	}
	
	private void setEmptyLabel() {
		noOpenTagLibraries.setSizeFull();
		setMargin(true);
		addComponent(noOpenTagLibraries);
		setComponentAlignment(noOpenTagLibraries, Alignment.MIDDLE_CENTER);
	}

	public void openTagLibrary(TagLibrary tagLibrary) {
		TagLibraryView tagLibraryView = getTagLibraryView(tagLibrary);
		if (tagLibraryView != null) {
			tabSheet.setSelectedTab(tagLibraryView);
		}
		else {
			tagLibraryView = new TagLibraryView(tagLibrary, getApplication());
			Tab tab = tabSheet.addTab(tagLibraryView, tagLibrary.getName());
			tab.setClosable(true);
			tabSheet.setSelectedTab(tab.getComponent());
		}
		
		if (tabSheet.getParent() == null) {
			removeComponent(noOpenTagLibraries);
			addComponent(tabSheet);
			setMargin(false);
		}
	}
	
	
	private TagLibraryView getTagLibraryView(TagLibrary tagLibrary) {
		Iterator<Component> iterator = tabSheet.getComponentIterator();
		while (iterator.hasNext()) {
			Component c = iterator.next();
			TagLibraryView tagLibraryView = (TagLibraryView)c;
			if (tagLibraryView.getTagLibraryName().equals(tagLibrary.getName())) {
				return tagLibraryView;
			}
		}
		
		return null;
	}
	

	public void onTabClose(TabSheet tabsheet, Component tabContent) {
		// workaround for http://dev.vaadin.com/ticket/7686
		
//		if (tabContent.equals(tabsheet.getSelectedTab())) {
//			tabsheet.removeComponent(tabContent);
//		}
//		else {
//			tabsheet.setSelectedTab(tabContent);
//		}
		
		tabsheet.removeComponent(tabContent);
		try {
			Thread.sleep(5);
		} catch (InterruptedException ex) {
	            //do nothing 
	    }
		if (tabsheet.getComponentCount() == 0) {
			removeComponent(tabsheet);
			setEmptyLabel();
		}

	}

}