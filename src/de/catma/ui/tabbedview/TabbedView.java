package de.catma.ui.tabbedview;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TabSheet.CloseHandler;
import com.vaadin.ui.TabSheet.SelectedTabChangeEvent;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.TabSheet.Tab;
import com.vaadin.ui.VerticalLayout;

public class TabbedView extends VerticalLayout implements CloseHandler, Iterable<Component> {
	
	private TabSheet tabSheet;
	private Label noOpenTabsLabel;
	private TabComponent lastTab;
	
	public TabbedView(String noOpenTabsText) {
		initComponents(noOpenTabsText);
		initActions();
	}

	private void initActions() {
		tabSheet.addListener(new SelectedTabChangeListener() {
			
			public void selectedTabChange(SelectedTabChangeEvent event) {
				if (lastTab != null) {
					lastTab.removeClickshortCuts();
				}
				lastTab = (TabComponent)tabSheet.getSelectedTab();
				lastTab.addClickshortCuts();
			}
		});
	}

	private void initComponents(String noOpenTabsText) {
		tabSheet = new TabSheet();
		tabSheet.setSizeFull();
		noOpenTabsLabel = 
				new Label(noOpenTabsText);
		
		noOpenTabsLabel.setSizeFull();
		setMargin(true);
		addComponent(noOpenTabsLabel);
		setComponentAlignment(noOpenTabsLabel, Alignment.MIDDLE_CENTER);
		
		addComponent(tabSheet);
		tabSheet.hideTabs(true);
		tabSheet.setHeight("0px");	
		tabSheet.setCloseHandler(this);
		
		setSizeFull();
	}
	
	protected void onTabClose(Component tabContent) {
		onTabClose(tabSheet, tabContent);
	}
	
	public void onTabClose(TabSheet tabsheet, Component tabContent) {
		tabsheet.removeComponent(tabContent);
		((ClosableTab)tabContent).close();

		if (tabsheet.getComponentCount() == 0) {
			 //setVisible(false) doesn't work here because of out of sync errors
			tabSheet.hideTabs(true);
			tabSheet.setHeight("0px");
			addComponent(noOpenTabsLabel, 0);
			noOpenTabsLabel.setVisible(true);
			setMargin(true);
		}
		
	}
	
	protected Tab addTab(TabComponent component, String caption) {
		
		Tab tab = tabSheet.addTab(component, caption);
		
		tab.setClosable(false);
		tabSheet.setSelectedTab(tab.getComponent());
		
		if (tabSheet.getComponentCount() != 0) {
			noOpenTabsLabel.setVisible(false);
			removeComponent(noOpenTabsLabel);
			setMargin(false);
			tabSheet.hideTabs(false);
			tabSheet.setSizeFull();
		}
		
		return tab;
	}
	
	protected Tab addClosableTab(ClosableTab closableTab, String caption) {
		Tab tab = addTab(closableTab, caption);
		tab.setClosable(true);
		return tab;
	}

	public Iterator<Component> iterator() {
		return tabSheet.getComponentIterator();
	}
	
	protected void setSelectedTab(Component tabContent) {
		tabSheet.setSelectedTab(tabContent);
	}
	
	protected int getTabPosition(Tab tab) {
		return tabSheet.getTabPosition(tab);
	}

	public Component getComponent(int position) {
		if (tabSheet.getComponentCount() > position) {
			return tabSheet.getTab(position).getComponent();
		}
		else {
			return null;
		}
	}
	
	public String getCaption(Component c) {
		Tab t = tabSheet.getTab(c);
		if (t != null) {
			return t.getCaption();
		}
		else {
			return "";
		}
	}
	
	public void closeClosables() {
		Set<Component> componentBuffer = new HashSet<Component>();
		for (Component comp : this) {
			componentBuffer.add(comp);
		}
		for (Component comp : componentBuffer) {
			if (comp instanceof ClosableTab) {
				onTabClose(tabSheet, comp);
			}
		}
	}
}
