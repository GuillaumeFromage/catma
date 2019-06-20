package de.catma.ui.modules.tags;

import com.vaadin.data.provider.TreeDataProvider;

interface TagsetTreeItem {
	public String getColor();
	public String getName();
	public String getTagsetName();
	public String getPropertySummary();
	
	public default void removePropertyDataItem(TreeDataProvider<TagsetTreeItem> dataProvider) {}
	public default String generateStyle() { return null; }
	public default String getPropertyValue() { return null; }

}
