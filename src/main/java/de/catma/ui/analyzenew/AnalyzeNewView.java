package de.catma.ui.analyzenew;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.antlr.runtime.RecognitionException;
import org.apache.bcel.verifier.VerificationResult;

import com.vaadin.data.HasValue.ValueChangeEvent;
import com.vaadin.event.selection.SingleSelectionEvent;
import com.vaadin.event.selection.SingleSelectionListener;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.FontAwesome;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.ComboBox.NewItemHandler;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.UI;
import com.vaadin.ui.themes.ValoTheme;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.TextField;
import com.vaadin.v7.data.util.HierarchicalContainer;
import com.vaadin.v7.ui.Tree;
import com.vaadin.v7.ui.VerticalLayout;

import de.catma.backgroundservice.BackgroundServiceProvider;
import de.catma.backgroundservice.ExecutionListener;
import de.catma.document.Corpus;
import de.catma.document.source.IndexInfoSet;
import de.catma.document.source.SourceDocument;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollectionReference;
import de.catma.indexer.IndexedRepository;
import de.catma.queryengine.QueryJob;
import de.catma.queryengine.QueryOptions;
import de.catma.queryengine.QueryJob.QueryException;
import de.catma.queryengine.result.GroupedQueryResultSet;
import de.catma.queryengine.result.QueryResult;
import de.catma.ui.CatmaApplication;
import de.catma.ui.analyzer.GroupedQueryResultSelectionListener;
import de.catma.ui.analyzer.Messages;
import de.catma.ui.analyzer.RelevantUserMarkupCollectionProvider;
import de.catma.ui.analyzer.TagKwicResultsProvider;
import de.catma.ui.component.HTMLNotification;
import de.catma.ui.repository.MarkupCollectionItem;
import de.catma.ui.tabbedview.ClosableTab;
import de.catma.ui.tabbedview.TabComponent;

public class AnalyzeNewView extends VerticalLayout 
implements ClosableTab, TabComponent, GroupedQueryResultSelectionListener, RelevantUserMarkupCollectionProvider, TagKwicResultsProvider {
	
	public static interface CloseListenerNew{
		public void closeRequest(AnalyzeNewView analyzeNewView);
	}
	private String userMarkupItemDisplayString="Markup Collections";
	private IndexedRepository repository;
	private Corpus corpus;
	private CloseListenerNew closeListener2;
	private List<String> relevantSourceDocumentIDs;
	private List<String> relevantUserMarkupCollIDs;
	private List<String> relevantStaticMarkupCollIDs;
	private IndexInfoSet indexInfoSet;
	private Button btExecuteSearch;
	private Button btQueryBuilder;
	private Button kwicBt;
	private Button distBt;
	private Button wordCloudBt;
	private Button networkBt;
	private TextField searchInput;
	private ComboBox<String > queryComboBox;
	private ResultPanelNew  queryResultPanel;
	private HorizontalLayout resultAndVisualizationPanel;
	private VerticalLayout resultPanel;
    private VerticalLayout visualizationPanel;
    private  MarginInfo margin;
	
	
	 public AnalyzeNewView(Corpus corpus, IndexedRepository repository, CloseListenerNew closeListener2) throws Exception{
			this.corpus = corpus;
			this.repository= repository;
			this.closeListener2 = closeListener2;
			this.relevantSourceDocumentIDs = new ArrayList<String>();
			this.relevantUserMarkupCollIDs = new ArrayList<String>();
			this.relevantStaticMarkupCollIDs = new ArrayList<String>();
			this.indexInfoSet = 
					new IndexInfoSet(
						Collections.<String>emptyList(), 
						Collections.<Character>emptyList(), 
						Locale.ENGLISH);
			
			initComponents();
			initListeners();
			initActions();
	
	}

	 private void initComponents() throws Exception{
			margin = new MarginInfo(true, true, true, true);
		 	createHeaderInfo();
		 	
		 	Component searchPanel = createSearchPanel();
		    Component visIconsPanel=	createVisIconsPanel();		
		    
			HorizontalLayout searchAndVisIconsPanel = new HorizontalLayout();
			searchAndVisIconsPanel.addComponents(searchPanel, visIconsPanel);
			searchAndVisIconsPanel.setWidth("100%");
			searchAndVisIconsPanel.setExpandRatio(searchPanel,  1);
			searchAndVisIconsPanel.setExpandRatio(visIconsPanel,  1);
			searchAndVisIconsPanel.setMargin(margin);
			addComponent(searchAndVisIconsPanel);	
			
			resultAndVisualizationPanel = new HorizontalLayout();
			resultAndVisualizationPanel.setWidth("100%");
		    resultPanel = new VerticalLayout();
		    resultPanel.setHeightUndefined(); 
	
		    visualizationPanel = new VerticalLayout();
			resultAndVisualizationPanel.addComponents(resultPanel,visualizationPanel);
			resultAndVisualizationPanel.setExpandRatio(resultPanel, 1);
			resultAndVisualizationPanel.setExpandRatio(visualizationPanel, 1);
		
			resultAndVisualizationPanel.setMargin(margin);
			setMargin(true);
			addComponent(resultAndVisualizationPanel);		
	 
	 }
	 
	 private void initListeners() {	 
		 
		 	queryComboBox.addValueChangeListener(event -> {
         if (event.getSource().isEmpty()) {
                 // show some message
         } else {
        String predefQueryString= event.getSource().getValue();
        searchInput.setValue(predefQueryString);
         }
     });
		 	
		 kwicBt.addClickListener(new ClickListener() {		
			public void buttonClick(ClickEvent event) {
				//create the KWIC PreviewBox
				}
			});
		 
	 }
	 private void initActions() {
		 btExecuteSearch.addClickListener(new ClickListener() {
				
				public void buttonClick(ClickEvent event) {
					searchInput.getValue().toString();
					
				      // String predefQuery=  event.getSource().toString();
			          // searchInput.setValue(predefQuery);
					executeSearch();
				}

			});
		 
			/*btQueryBuilder.addClickListener(new ClickListener() {
				
				public void buttonClick(ClickEvent event) {
					//showQueryBuilder();
				}
			});*/
		 
	 }
	 
	 private void createHeaderInfo() throws Exception {
		 //	documentsContainer = new HierarchicalContainer();
		//	documentsTree = new Tree();
		//	documentsTree.setContainerDataSource(documentsContainer);
		//	documentsTree.setCaption(
				//	Messages.getString("AnalyzerView.docsConstrainingThisSearch")); //$NON-NLS-1$
			
			if (corpus != null) {
				for (SourceDocument sd : corpus.getSourceDocuments()) {
					addSourceDocument(sd);
				}
			}
			else {
				for (SourceDocument sd : repository.getSourceDocuments()) {
					addSourceDocument(sd);
				}
				//documentsTree.addItem(Messages.getString("AnalyzerView.AllDocuments")); //$NON-NLS-1$
			}
		 
	 }
		private void addSourceDocument(SourceDocument sd) {
			relevantSourceDocumentIDs.add(sd.getID());
			//TODO: provide a facility where the user can select between different IndexInfoSets
			indexInfoSet = 
					sd.getSourceContentHandler().getSourceDocumentInfo().getIndexInfoSet();
			
			//documentsTree.addItem(sd);
			MarkupCollectionItem umc = 
				new MarkupCollectionItem(
						sd, 
						userMarkupItemDisplayString, true);
		//	documentsTree.addItem(umc);
		//	documentsTree.setParent(umc, sd);
			for (UserMarkupCollectionReference umcRef :
				sd.getUserMarkupCollectionRefs()) {
				if (corpus.getUserMarkupCollectionRefs().contains(umcRef)) {
					addUserMarkupCollection(umcRef, umc);
				}
			}
		}
		private void addUserMarkupCollection(UserMarkupCollectionReference umcRef,
				MarkupCollectionItem umc) {
			this.relevantUserMarkupCollIDs.add(umcRef.getId());
		}
	 
	 private void  createResultView() {
		 
	 }
	 
	@Override
	public void tagResults() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<String> getRelevantUserMarkupCollectionIDs() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Corpus getCorpus() {
	return corpus;
	}
	
	private Component createSearchPanel() {
		VerticalLayout searchPanel = new VerticalLayout();
		Label searchPanelLabel = new Label ("Queries");
		
		HorizontalLayout searchRow = new HorizontalLayout();
		searchRow.setWidth("100%");
		
		searchInput = new TextField();
		btQueryBuilder = new Button ("+ BUILD QUERY");
		btQueryBuilder.setStyleName("body");
		
	
		List<String> predefQueries = new ArrayList<>();
		predefQueries.add(new String("tag=\"Tag1\""));
		predefQueries.add(new String("wild = \"Blumen\""));
		predefQueries.add(new String("freq>0"));
		
		queryComboBox = new ComboBox<>();
		queryComboBox.setItems(predefQueries);		
		queryComboBox.setNewItemHandler( new NewItemHandler() {
			@Override
			public void accept(String t) {	
			searchInput.setValue(t);
			}
		});
		
		queryComboBox.addSelectionListener(new SingleSelectionListener<String>() {
			@Override
			public void selectionChange(SingleSelectionEvent<String> event) {
			searchInput.setValue(	event.getValue());
				
			}
		});
		
		btExecuteSearch = new Button ("SEARCH",VaadinIcons.SEARCH);
		btExecuteSearch.setWidth("100%");
		btExecuteSearch.setStyleName("primary");

		queryComboBox.setWidth("100%");
		searchRow.addComponents(btQueryBuilder,queryComboBox);
		searchRow.setComponentAlignment(queryComboBox, Alignment.MIDDLE_CENTER);
		searchRow.setExpandRatio(queryComboBox, 0.6f);
		searchPanel.addComponents(searchPanelLabel,searchRow,btExecuteSearch);	
		return searchPanel;
	}
	
	private Component createVisIconsPanel() {
		VerticalLayout visIconsPanel = new VerticalLayout();
		Label visIconsLabel = new Label ("Visualisations");
		visIconsLabel.setWidth("100%");
		HorizontalLayout visIconBar = new HorizontalLayout();
		
		kwicBt = new Button("KWIC",VaadinIcons.SPLIT);
		kwicBt.addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
		kwicBt.addStyleName(ValoTheme.BUTTON_BORDERLESS);
		kwicBt.setWidth("100%");
		kwicBt.setHeight("100%");
		
		distBt= new Button("DISTRIBUTION",VaadinIcons.CHART_LINE);
		distBt.addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
		distBt.addStyleName(ValoTheme.BUTTON_BORDERLESS);
		distBt.setWidth("100%");
		distBt.setHeight("100%");
		
		wordCloudBt= new Button("WORDCLOUD",VaadinIcons.CLOUD);
		wordCloudBt.addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
		wordCloudBt.addStyleName(ValoTheme.BUTTON_BORDERLESS);
		wordCloudBt.setWidth("100%");
		wordCloudBt.setHeight("100%");
		
		networkBt= new Button("NETWORK",VaadinIcons.CLUSTER);
		networkBt.addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
		networkBt.addStyleName(ValoTheme.BUTTON_BORDERLESS);
		networkBt.setWidth("100%");
		networkBt.setHeight("100%");
		
/*		VerticalLayout barchartIconLayout = new VerticalLayout();
		Label icon = new Label(FontAwesome.BAR_CHART.getHtml(), ContentMode.HTML);
		icon.addStyleName(ValoTheme.LABEL_H1);
		icon.setHeight("100%");
		icon.setWidth("100%");
		Label iconText = new Label("barchart");
		barchartIconLayout.addComponents(icon,iconText);*/
		
		
		visIconBar.addComponents(kwicBt,distBt,wordCloudBt,networkBt);
		visIconBar.setWidth("100%");
		visIconBar.setHeight("100%");
		
		visIconsPanel.addComponent(visIconsLabel);	
		visIconsPanel.setComponentAlignment(visIconsLabel, Alignment.MIDDLE_CENTER);
		visIconsPanel.addComponent(visIconBar);
		visIconsPanel.setHeight("100%");
		return visIconsPanel;	
	}

	@Override
	public void resultsSelected(GroupedQueryResultSet groupedQueryResultSet) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addClickshortCuts() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeClickshortCuts() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}
	
	private void executeSearch() {

		QueryOptions queryOptions = new QueryOptions(
				relevantSourceDocumentIDs,
				relevantUserMarkupCollIDs,
				relevantStaticMarkupCollIDs,
				indexInfoSet.getUnseparableCharacterSequences(),
				indexInfoSet.getUserDefinedSeparatingCharacters(),
				indexInfoSet.getLocale(),
				repository);

		
		QueryJob job = new QueryJob(
				searchInput.getValue().toString(),
				queryOptions);
		
		((BackgroundServiceProvider)UI.getCurrent()).submit(
				Messages.getString("AnalyzerView.Searching"), //$NON-NLS-1$
				job, 
				new ExecutionListener<QueryResult>() {
			public void done(QueryResult result) {
			 queryResultPanel = new ResultPanelNew(result,"result for query: "+searchInput.getValue());
			 queryResultPanel.setWidth("100%");
			 resultPanel.setSpacing(true);
			 resultPanel.addComponentAsFirst(queryResultPanel);
			   	
			};
			public void error(Throwable t) {
			
				if (t instanceof QueryException) {
					QueryJob.QueryException qe = (QueryJob.QueryException)t;
		            String input = qe.getInput();
		            int idx = ((RecognitionException)qe.getCause()).charPositionInLine;
		            if ((idx >=0) && (input.length() > idx)) {
		                char character = input.charAt(idx);
		            	String message = MessageFormat.format(
		            		Messages.getString("AnalyzerView.queryFormatError"), //$NON-NLS-1$
		            		input,
	                        idx+1,
	                        character);
						HTMLNotification.show(
		            			Messages.getString("AnalyzerView.InfoTitle"), message, Type.TRAY_NOTIFICATION); //$NON-NLS-1$
		            }
		            else {
		            	String message = MessageFormat.format(
		            			Messages.getString("AnalyzerView.generalQueryFormatError"), //$NON-NLS-1$
			            		input);
						HTMLNotification.show(
		            			Messages.getString("AnalyzerView.InfoTitle"), message, Type.TRAY_NOTIFICATION); //$NON-NLS-1$
		            }
				}
				else {
					((CatmaApplication)UI.getCurrent()).showAndLogError(
						Messages.getString("AnalyzerView.errorDuringSearch"), t); //$NON-NLS-1$
				}
			}
		});
	
	}

}
