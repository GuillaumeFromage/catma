package de.catma.ui.analyzenew.queryresultpanel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.common.cache.LoadingCache;
import com.vaadin.data.TreeData;

import de.catma.indexer.KwicProvider;
import de.catma.queryengine.result.QueryResultRow;
import de.catma.queryengine.result.QueryResultRowArray;
import de.catma.queryengine.result.TagQueryResultRow;

public class KwicPropertiesAsColumnsQueryResultRowItem implements QueryResultRowItem {

	private final QueryResultRow masterRow;
	private String kwic;
	private String detailedKwic;
	private String documentName;
	private String collectionName;
	private QueryResultRowArray rows;
	private Map<String,String> propertyValueByName;

	public KwicPropertiesAsColumnsQueryResultRowItem(
			QueryResultRowArray rows, String kwic, String detailedKwic, 
			String documentName, String collectionName) {
		this.masterRow = rows.get(0);
		this.rows = rows;
		this.kwic = kwic;
		this.detailedKwic = detailedKwic;
		this.documentName = documentName;
		this.collectionName = collectionName;
		initPropertyValueByName();
	}

	private void initPropertyValueByName() {
		this.propertyValueByName = new HashMap<String, String>();
		for (QueryResultRow row : rows) {
			if (row instanceof TagQueryResultRow) {
				TagQueryResultRow tRow = (TagQueryResultRow) row;
				if (tRow.getPropertyDefinitionId() != null) {
					propertyValueByName.put(tRow.getPropertyName(), tRow.getPropertyValue());
				}
			}
		}
	}

	@Override
	public String getKey() {
		return this.kwic;
	}

	@Override
	public int getFrequency() {
		return 1;
	}

	@Override
	public QueryResultRowArray getRows() {
		return rows;
	}

	@Override
	public Integer getStartOffset() {
		return masterRow.getRange().getStartPoint();
	}
	
	@Override
	public Integer getEndOffset() {
		return masterRow.getRange().getEndPoint();
	}

	@Override
	public String getDetailedKeyInContext() {
		return detailedKwic;
	}

	@Override
	public boolean isExpansionDummy() {
		return false;
	}


	@Override
	public void addChildRowItems(TreeData<QueryResultRowItem> treeData,
			LoadingCache<String, KwicProvider> kwicProviderCache) {
		// no children
	}
	
	@Override
	public String getCollectionName() {
		return collectionName;
	}
	
	@Override
	public String getDocumentName() {
		return documentName;
	}
	
	@Override
	public String getTagPath() {
		if (masterRow instanceof TagQueryResultRow) {
			return ((TagQueryResultRow) masterRow).getTagDefinitionPath();
		}
		return QueryResultRowItem.super.getTagPath();
	}
	
	@Override
	public String getPropertyValue(String propertyName) {
		return propertyValueByName.get(propertyName);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((masterRow == null) ? 0 : masterRow.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof KwicPropertiesAsColumnsQueryResultRowItem))
			return false;
		KwicPropertiesAsColumnsQueryResultRowItem other = (KwicPropertiesAsColumnsQueryResultRowItem) obj;
		if (masterRow == null) {
			if (other.masterRow != null)
				return false;
		} else if (!masterRow.equals(other.masterRow))
			return false;
		return true;
	}
	
	@Override
	public void addQueryResultRow(QueryResultRow row, TreeData<QueryResultRowItem> treeData,
			LoadingCache<String, KwicProvider> kwicProviderCache) {

		if (row instanceof TagQueryResultRow) {
			TagQueryResultRow tRow = (TagQueryResultRow) row;
			if (tRow.getTagInstanceId().equals(((TagQueryResultRow) masterRow).getTagInstanceId())) {
				rows.add(tRow);
				initPropertyValueByName();
			}
		}
	}
	
	@Override
	public void removeQueryResultRow(QueryResultRow row, TreeData<QueryResultRowItem> treeData) {
		rows.remove(row);
	}
}
