package de.catma.ui.analyzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.vaadin.data.util.HierarchicalContainer;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.HorizontalSplitPanel;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Tree;
import com.vaadin.ui.VerticalLayout;

import de.catma.CatmaApplication;
import de.catma.backgroundservice.BackgroundServiceProvider;
import de.catma.backgroundservice.ExecutionListener;
import de.catma.document.Corpus;
import de.catma.document.source.SourceDocument;
import de.catma.document.standoffmarkup.staticmarkup.StaticMarkupCollectionReference;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollectionReference;
import de.catma.indexer.IndexedRepository;
import de.catma.queryengine.QueryJob;
import de.catma.queryengine.QueryOptions;
import de.catma.queryengine.result.GroupedQueryResultSet;
import de.catma.queryengine.result.QueryResult;
import de.catma.queryengine.result.computation.DistributionComputation;
import de.catma.ui.analyzer.PhraseResultPanel.VisualizeGroupedQueryResultSelectionListener;
import de.catma.ui.repository.MarkupCollectionItem;
import de.catma.ui.tabbedview.ClosableTab;

public class AnalyzerView extends VerticalLayout implements ClosableTab {
	
	private String userMarkupItemDisplayString = "User Markup Collections";
	private String staticMarkupItemDisplayString = "Static Markup Collections";
	private TextField searchInput;
	private Button btExecSearch;
	private Button btQueryBuilder;
	private Button btWordList;
	private HierarchicalContainer documentsContainer;
	private Tree documentsTree;
	private TabSheet resultTabSheet;
	private PhraseResultPanel phraseResultPanel;
	private IndexedRepository repository;
	private List<String> relevantSourceDocumentIDs;
	private List<String> relevantUserMarkupCollIDs;
	private List<String> relevantStaticMarkupCollIDs;
	private MarkupResultPanel markupResultPanel;
	private String name;
	private Integer visualizationId;
	
	public AnalyzerView(
			Corpus corpus, IndexedRepository repository) {
		this.name = corpus.toString();
		
		this.relevantSourceDocumentIDs = new ArrayList<String>();
		this.relevantUserMarkupCollIDs = new ArrayList<String>();
		this.relevantStaticMarkupCollIDs = new ArrayList<String>();
		
		if (corpus != null) {
			for (SourceDocument sd : corpus.getSourceDocuments()) {
				this.relevantSourceDocumentIDs.add(sd.getID());
			}
			for (UserMarkupCollectionReference ref : 
				corpus.getUserMarkupCollectionRefs()) {
				this.relevantUserMarkupCollIDs.add(ref.getId());
			}
			for (StaticMarkupCollectionReference ref : 
				corpus.getStaticMarkupCollectionRefs()) {
				this.relevantStaticMarkupCollIDs.add(ref.getId());
			}
		}
		
		this.repository = repository;
		initComponents(corpus);
		initActions();
	}
	

	private void initActions() {
		btExecSearch.addListener(new ClickListener() {
			
			public void buttonClick(ClickEvent event) {
				executeSearch();
			}

		});
		btWordList.addListener(new ClickListener() {
			
			public void buttonClick(ClickEvent event) {
				searchInput.setValue("freq>0");
				executeSearch();
			}

		});
		btQueryBuilder.addListener(new ClickListener() {
			
			public void buttonClick(ClickEvent event) {
				showQueryBuilder();
			}
		});
	}

	private void showQueryBuilder() {
		//TODO: impl
	}


	private void executeSearch() {
		List<String> unseparableCharacterSequences = Collections.emptyList();
		List<Character> userDefinedSeparatingCharacters = Collections.emptyList();
		QueryOptions queryOptions = new QueryOptions(
				relevantSourceDocumentIDs,
				relevantUserMarkupCollIDs,
				relevantStaticMarkupCollIDs,
				unseparableCharacterSequences,
				userDefinedSeparatingCharacters,
				Locale.ENGLISH,
				repository);
		
		QueryJob job = new QueryJob(
				searchInput.getValue().toString(),
				queryOptions);
		
		((BackgroundServiceProvider)getApplication()).submit(
				"Searching...",
				job, 
				new ExecutionListener<QueryResult>() {
			public void done(QueryResult result) {
				phraseResultPanel.setQueryResult(result);
				//TODO: lazy?!
				try {
					markupResultPanel.setQueryResult(result);
				} catch (IOException e) {
					((CatmaApplication)getApplication()).showAndLogError(
						"Error accessing the repository!", e);
				} 
			};
			public void error(Throwable t) {
				((CatmaApplication)getApplication()).showAndLogError(
					"Error during search!", t);
			}
		});
	}
	
	private void initComponents(Corpus corpus) {
		setSizeFull();
		
		Component searchPanel = createSearchPanel();
		
		Component convenienceButtonPanel = createConvenienceButtonPanel();
		
		VerticalLayout searchAndConveniencePanel = new VerticalLayout();
		searchAndConveniencePanel.setSpacing(true);
		searchAndConveniencePanel.setMargin(true);
		searchAndConveniencePanel.addComponent(searchPanel);
		searchAndConveniencePanel.addComponent(convenienceButtonPanel);
		
		Component documentsPanel = createDocumentsPanel(corpus);

		HorizontalSplitPanel topPanel = new HorizontalSplitPanel();
		topPanel.setSplitPosition(70);
		topPanel.addComponent(searchAndConveniencePanel);
		topPanel.addComponent(documentsPanel);
		addComponent(topPanel);
		
		setExpandRatio(topPanel, 0.25f);
		
		Component resultPanel = createResultPanel();
		resultPanel.setSizeFull();
		
		addComponent(resultPanel);
		setExpandRatio(resultPanel, 0.75f);
	}

	private Component createResultPanel() {
		
		resultTabSheet = new TabSheet();
		resultTabSheet.setSizeFull();
		
		Component resultByPhraseView = createResultByPhraseView();
		resultTabSheet.addTab(resultByPhraseView, "Result by phrase");
		
		Component resultByMarkupView = createResultByMarkupView();
		resultTabSheet.addTab(resultByMarkupView, "Result by markup");
		
		return resultTabSheet;
	}

	private Component createResultByMarkupView() {
		markupResultPanel = new MarkupResultPanel(repository);
		return markupResultPanel;
	}

	private Component createResultByPhraseView() {
		phraseResultPanel = new PhraseResultPanel(repository,
				new VisualizeGroupedQueryResultSelectionListener() {
					
					public void setSelected(GroupedQueryResultSet groupedQueryResultSet,
							boolean selected) {
						try {
							handleDistributionChartRequest(
									groupedQueryResultSet, selected);
						} catch (IOException e) {
							((CatmaApplication)getApplication()).showAndLogError(
								"Error showing the distribution chart",
								e);
						}
					}
				});
		return phraseResultPanel;
	}

	private Component createDocumentsPanel(Corpus corpus) {
		Panel documentsPanel = new Panel();
		
		documentsContainer = new HierarchicalContainer();
		documentsTree = new Tree();
		documentsTree.setContainerDataSource(documentsContainer);
		documentsTree.setCaption(
				"Documents and collections considered for this search");
		
		if (corpus != null) {
			for (SourceDocument sd : corpus.getSourceDocuments()) {
				documentsTree.addItem(sd);
				MarkupCollectionItem umc = 
					new MarkupCollectionItem(
							userMarkupItemDisplayString, true);
				documentsTree.addItem(umc);
				documentsTree.setParent(umc, sd);
				for (UserMarkupCollectionReference umcRef :
					sd.getUserMarkupCollectionRefs()) {
					if (corpus.getUserMarkupCollectionRefs().contains(umcRef)) {
						documentsTree.addItem(umcRef);
						documentsTree.setParent(umcRef, umc);
						documentsTree.setChildrenAllowed(umcRef, false);
					}
				}
			}
		}
		else {
			documentsTree.addItem("All documents");
		}
		
		documentsPanel.addComponent(documentsTree);
		return documentsPanel;
	}

	private Component createConvenienceButtonPanel() {
		HorizontalLayout convenienceButtonPanel = new HorizontalLayout();
		convenienceButtonPanel.setSpacing(true);
		
		btQueryBuilder = new Button("Query Builder");
		convenienceButtonPanel.addComponent(btQueryBuilder);
		
		btWordList = new Button("Wordlist");
		convenienceButtonPanel.addComponent(btWordList);
	
		return convenienceButtonPanel;
	}

	private Component createSearchPanel() {
		HorizontalLayout searchPanel = new HorizontalLayout();
		searchPanel.setSpacing(true);
		searchPanel.setWidth("100%");
		
		searchInput = new TextField();
		searchInput.setCaption("Query");
		searchInput.setWidth("100%");

		
		searchPanel.addComponent(searchInput);
		searchPanel.setExpandRatio(searchInput, 1.0f);
		
		btExecSearch = new Button("Execute Query");
		btExecSearch.setClickShortcut(KeyCode.ENTER);
		
		searchPanel.addComponent(btExecSearch);
		searchPanel.setComponentAlignment(btExecSearch, Alignment.BOTTOM_CENTER);
		
		return searchPanel;
	}

	private void handleDistributionChartRequest(
			GroupedQueryResultSet groupedQueryResultSet, boolean selected) throws IOException {

		DistributionComputation dc = new DistributionComputation(
				groupedQueryResultSet, repository, relevantSourceDocumentIDs);
		dc.compute();
		
		this.visualizationId = 
			((CatmaApplication)getApplication()).addVisulization(
				visualizationId, name, dc);
	}

	public void close() {
		// TODO Auto-generated method stub
		
	}

}