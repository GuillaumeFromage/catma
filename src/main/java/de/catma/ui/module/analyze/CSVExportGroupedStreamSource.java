package de.catma.ui.module.analyze;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.vaadin.server.StreamResource.StreamSource;
import com.vaadin.ui.UI;

import de.catma.backgroundservice.BackgroundServiceProvider;
import de.catma.backgroundservice.DefaultProgressCallable;
import de.catma.backgroundservice.ExecutionListener;
import de.catma.indexer.KwicProvider;
import de.catma.project.Project;
import de.catma.queryengine.result.QueryResult;
import de.catma.queryengine.result.QueryResultRow;
import de.catma.queryengine.result.TagQueryResultRow;
import de.catma.ui.module.analyze.queryresultpanel.TagQueryResultRowItem;
import de.catma.ui.module.main.ErrorHandler;

public class CSVExportGroupedStreamSource implements StreamSource {
	
	private final Supplier<QueryResult> queryResultSupplier;
	private final Project project;
	private final LoadingCache<String, KwicProvider> kwicProviderCache;
	private final BackgroundServiceProvider backgroundServiceProvider;
	private final Supplier<Boolean> groupByTagSupplier;
	
	public CSVExportGroupedStreamSource(
			Supplier<QueryResult> queryResultSupplier, Project project, Supplier<Boolean> groupByTagSupplier,
			LoadingCache<String, KwicProvider> kwicProviderCache, BackgroundServiceProvider backgroundServiceProvider) {
		super();
		this.queryResultSupplier = queryResultSupplier;
		this.project = project;
		this.groupByTagSupplier = groupByTagSupplier;
		this.kwicProviderCache = kwicProviderCache;
		this.backgroundServiceProvider = backgroundServiceProvider;
	}

	private int getValue(Table<String, String, Integer> groupings, String rowKey, String columnKey) {
		Integer value = groupings.get(rowKey, columnKey);
		if (value == null) {
			return 0;
		}
		
		return value;
	}
	
	@Override
	public InputStream getStream() {
		final QueryResult queryResult = queryResultSupplier.get();
		final Set<String> documentIds = new TreeSet<String>();
		final Multimap<String, String> collectionIdByDocumentId = ArrayListMultimap.create();
		final Table<String, String, Integer> groupings = HashBasedTable.create();
		
		for (QueryResultRow row : queryResult) {
			
			String group = row.getPhrase();
			
			// change group to tag path if "group by tag" is selected
			if (groupByTagSupplier.get()) {
				if (row instanceof TagQueryResultRow) {
					group = ((TagQueryResultRow) row).getTagDefinitionPath();
				}
				else {
					group = TagQueryResultRowItem.getNoTagAvailableKey();
				}
			}
			
			groupings.put(group, "Total", getValue(groupings, group, "Total")+1);
			groupings.put(group, row.getSourceDocumentId(), getValue(groupings, group, row.getSourceDocumentId())+1);
			documentIds.add(row.getSourceDocumentId());
			
			if (row instanceof TagQueryResultRow) {
				collectionIdByDocumentId.put(row.getSourceDocumentId(), ((TagQueryResultRow)row).getMarkupCollectionId());
				groupings.put(
						group, 
						((TagQueryResultRow)row).getMarkupCollectionId(), 
						getValue(groupings, group, 
								((TagQueryResultRow)row).getMarkupCollectionId())+1);
			}
		}
		
        final PipedInputStream in = new PipedInputStream();
        final UI ui = UI.getCurrent();
        final Lock lock = new ReentrantLock();
        final Condition sending  = lock.newCondition();
        lock.lock();

        backgroundServiceProvider.submit("csv-export", new DefaultProgressCallable<Void>() {
        	@Override
        	public Void call() throws Exception {
            	PipedOutputStream out = new PipedOutputStream(in);
            	OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
            	
            	ArrayList<String> header = new ArrayList<>();
            	header.add("Group");
            	header.add("Total");
            	
            	for (String documentId : documentIds) {
            		KwicProvider kwicProvider = kwicProviderCache.get(documentId);
            		header.add(kwicProvider.getSourceDocumentName() + " (" + documentId + ")");
            		for (String collectionId : new TreeSet<String>(collectionIdByDocumentId.get(documentId))) {
            			
            			header.add(kwicProvider.getSourceDocumentReference().getUserMarkupCollectionReference(collectionId).toString() + " (" +collectionId + ")");
            		}
            	}

                try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.EXCEL.builder().setDelimiter(';').setHeader(header.toArray(new String[]{})).build())) {
                	
                	for (String group : new TreeSet<String>(groupings.rowKeySet())) {
                		csvPrinter.print(group);
                		csvPrinter.print(groupings.get(group, "Total"));
                		for (String documentId : documentIds) {
                			csvPrinter.print(groupings.get(group, documentId));
                			for (String collectionId : new TreeSet<String>(collectionIdByDocumentId.get(documentId))) {
                				csvPrinter.print(groupings.get(group, collectionId));
                			}
                		}
                		csvPrinter.println();
                	}
    	
    	            csvPrinter.flush();  
	                lock.lock();
	                try {
	                	sending.signal();
	                }
	                finally {
	                	lock.unlock();
	                }

                }

        		return null; //intended
        	}
		}, new ExecutionListener<Void>() {
			@Override
			public void done(Void result) {
				// noop
			}
			@Override
			public void error(Throwable t) {
				((ErrorHandler) ui).showAndLogError("Error exporting data to CSV", t);
			}
		});

        
        // waiting on the background thread to send data to the pipe
		try {
			sending.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Logger.getLogger(getClass().getName()).log(Level.WARNING, "Error while waiting on CSV export", e);
		}
		finally {
			lock.unlock();
		}
        
        return in;
	}
}
