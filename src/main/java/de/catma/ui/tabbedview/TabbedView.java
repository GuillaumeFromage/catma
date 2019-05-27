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
package de.catma.ui.tabbedview;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.vaadin.elements.Element;
import org.vaadin.elements.ElementIntegration;
import org.vaadin.elements.Elements;
import org.vaadin.elements.Root;

import com.vaadin.icons.VaadinIcons;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TabSheet.CloseHandler;
import com.vaadin.ui.TabSheet.SelectedTabChangeEvent;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.TabSheet.Tab;
import com.vaadin.ui.VerticalLayout;

public class TabbedView extends VerticalLayout implements CloseHandler {
	public interface CloseableTabFactory extends Supplier<ClosableTab> {
		@Override
		default ClosableTab get() {
			return createCloseableTab();
		}

		ClosableTab createCloseableTab();

		default String getDefaultCaption() { return "No selection yet"; };
	}
	
	@Deprecated
	private static class SimpleLabelTab extends VerticalLayout implements ClosableTab {
		
		public SimpleLabelTab(String noOpenTabsText) {
			setSpacing(true);

			Label noOpenTabsLabel = new Label(noOpenTabsText);

			noOpenTabsLabel.setSizeFull();
			addComponent(noOpenTabsLabel);
			setComponentAlignment(noOpenTabsLabel, Alignment.MIDDLE_CENTER);
		}
		
		@Override
		public void addClickshortCuts() {
		}
		@Override
		public void removeClickshortCuts() {
		}
		@Override
		public void close() {
		}
	}

	private TabSheet tabSheet;
	private TabComponent lastTab;
	private CloseableTabFactory closeableTabFactory;

	@Deprecated
	public TabbedView(final String noOpenTabsText) {
		this (new CloseableTabFactory() {
			
			@Override
			public ClosableTab createCloseableTab() {
				return new SimpleLabelTab(noOpenTabsText);
			}
		});

	}
	
	public TabbedView(CloseableTabFactory closeableTabFactory) {
		this.closeableTabFactory = closeableTabFactory;
		initComponents();
		initActions();	
	}
	
	
	private void initActions() {

		tabSheet.addSelectedTabChangeListener(new SelectedTabChangeListener() {

			public void selectedTabChange(SelectedTabChangeEvent event) {
				if (lastTab != null) {
					lastTab.removeClickshortCuts();
				}
				lastTab = (TabComponent) tabSheet.getSelectedTab();
				lastTab.addClickshortCuts();
			}
		});

	}

	private void initComponents() {
		addStyleName("hugecard-tabbed-view");	
		setMargin(false);
		setSizeFull();
		
		tabSheet = new TabSheet();
		tabSheet.setSizeFull();
		tabSheet.addStyleName("hugecard-tabbed-view-tabsheet");
		tabSheet.setCloseHandler(this);
		tabSheet.setTabsVisible(true);
		tabSheet.setSizeFull();
		
		addComponent(tabSheet);
		setExpandRatio(tabSheet, 1.0f);
		
	}
	
	@Override
	public void attach() {
		super.attach();
		Root tabSheetRoot = ElementIntegration.getRoot(tabSheet);
		
		tabSheetRoot.fetchDom(() -> {
			if (!tabSheetRoot.getChildren().isEmpty()) {
				Element tabContainer = (Element)tabSheetRoot.getChildren().get(0);
				Element addButtonElement = Elements.create("div");
				addButtonElement.setAttribute(
					"class", 
					"c-tabbed-view-plus v-button v-widget icon-only "
					+ "v-button-icon-only button__icon v-button-button__icon "
					+ "flat v-button-flat borderless v-button-borderless");
				
				tabContainer.appendChild(addButtonElement);
				addButtonElement.setInnerHtml(VaadinIcons.PLUS.getHtml());

				addButtonElement.addEventListener("click", args -> {
		           addTab(closeableTabFactory.createCloseableTab(), closeableTabFactory.getDefaultCaption());
				});
			}
		}, tabSheet);

		if (tabSheet.getComponentCount() == 0) {
			addClosableTab(closeableTabFactory.createCloseableTab(), closeableTabFactory.getDefaultCaption());
		}
	}
	
	@Deprecated
	public void setHtmlLabel(){
	   // noOpenTabsLabel.setContentMode(ContentMode.HTML);
	}

	protected void onTabClose(Component tabContent) {
		onTabClose(tabSheet, tabContent);
	}

	public void onTabClose(TabSheet tabsheet, Component tabContent) {
		tabsheet.removeComponent(tabContent);
		((ClosableTab) tabContent).close();
		
		if ((lastTab != null) && lastTab.equals(tabContent)) {
			lastTab = null;
		}

		if (tabsheet.getComponentCount() == 0) {
			addClosableTab(closeableTabFactory.createCloseableTab(), closeableTabFactory.getDefaultCaption());
		}

	}

	protected Tab addTab(TabComponent component, String caption) {

		Tab tab = tabSheet.addTab(component, caption);

		tab.setClosable(false);
		tabSheet.setSelectedTab(tab.getComponent());

		return tab;
	}

	protected Tab addClosableTab(ClosableTab closableTab, String caption) {
		Tab tab = addTab(closableTab, caption);
		tab.setClosable(true);
		return tab;
	}

	public Iterable<Component> getTabSheet() {
		return tabSheet;
	}

	protected void setSelectedTab(Component tabContent) {
		tabSheet.setSelectedTab(tabContent);
	}

	public Component getSelectedTab() {
		return tabSheet.getSelectedTab();
	}

	protected int getTabPosition(Tab tab) {
		return tabSheet.getTabPosition(tab);
	}

	public Component getComponent(int position) {
		if (tabSheet.getComponentCount() > position) {
			return tabSheet.getTab(position).getComponent();
		} else {
			return null;
		}
	}
	
	protected void setCaption(Component tabContent, String caption) {
		Optional.ofNullable(tabSheet.getTab(tabContent))
		.ifPresent(tab ->tab.setCaption(caption));
	}

	public String getCaption(Component c) {
		Tab t = tabSheet.getTab(c);
		if (t != null) {
			return t.getCaption();
		} else {
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

	@Deprecated
	protected VerticalLayout getIntroPanel() {
		return null;
	}
}
