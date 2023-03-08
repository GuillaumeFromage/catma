package de.catma.ui.module.project;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.vaadin.contextmenu.ContextMenu;
import com.vaadin.data.TreeData;
import com.vaadin.data.provider.HierarchicalQuery;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.data.provider.TreeDataProvider;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.Page;
import com.vaadin.server.SerializablePredicate;
import com.vaadin.server.StreamResource;
import com.vaadin.ui.*;
import com.vaadin.ui.Grid.ItemClick;
import com.vaadin.ui.Grid.SelectionMode;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.renderers.HtmlRenderer;
import de.catma.backgroundservice.*;
import de.catma.document.annotation.AnnotationCollection;
import de.catma.document.annotation.AnnotationCollectionReference;
import de.catma.document.annotation.TagReference;
import de.catma.document.corpus.Corpus;
import de.catma.document.source.*;
import de.catma.document.source.contenthandler.BOMFilterInputStream;
import de.catma.document.source.contenthandler.SourceContentHandler;
import de.catma.document.source.contenthandler.TikaContentHandler;
import de.catma.document.source.contenthandler.XML2ContentHandler;
import de.catma.indexer.IndexedProject;
import de.catma.project.OpenProjectListener;
import de.catma.project.Project;
import de.catma.project.Project.ProjectEvent;
import de.catma.project.ProjectReference;
import de.catma.project.ProjectsManager;
import de.catma.project.event.ChangeType;
import de.catma.project.event.CollectionChangeEvent;
import de.catma.project.event.DocumentChangeEvent;
import de.catma.project.event.ProjectReadyEvent;
import de.catma.properties.CATMAPropertyKey;
import de.catma.rbac.RBACPermission;
import de.catma.rbac.RBACRole;
import de.catma.serialization.TagsetDefinitionImportStatus;
import de.catma.tag.*;
import de.catma.tag.TagManager.TagManagerEvent;
import de.catma.ui.CatmaApplication;
import de.catma.ui.Parameter;
import de.catma.ui.component.IconButton;
import de.catma.ui.component.TreeGridFactory;
import de.catma.ui.component.actiongrid.ActionGridComponent;
import de.catma.ui.component.hugecard.HugeCard;
import de.catma.ui.dialog.BeyondResponsibilityConfirmDialog;
import de.catma.ui.dialog.BeyondResponsibilityConfirmDialog.Action;
import de.catma.ui.dialog.GenericUploadDialog;
import de.catma.ui.dialog.SaveCancelListener;
import de.catma.ui.dialog.SingleTextInputDialog;
import de.catma.ui.dialog.wizard.WizardContext;
import de.catma.ui.events.HeaderContextChangeEvent;
import de.catma.ui.events.MembersChangedEvent;
import de.catma.ui.events.routing.RouteToAnalyzeEvent;
import de.catma.ui.events.routing.RouteToAnnotateEvent;
import de.catma.ui.events.routing.RouteToTagsEvent;
import de.catma.ui.layout.FlexLayout.FlexWrap;
import de.catma.ui.layout.HorizontalFlexLayout;
import de.catma.ui.layout.VerticalFlexLayout;
import de.catma.ui.module.main.CanReloadAll;
import de.catma.ui.module.main.ErrorHandler;
import de.catma.ui.module.project.corpusimport.CorpusImportDialog;
import de.catma.ui.module.project.corpusimport.CorpusImportDocumentMetadata;
import de.catma.ui.module.project.corpusimport.CorpusImporter;
import de.catma.ui.module.project.documentwizard.DocumentWizard;
import de.catma.ui.module.project.documentwizard.TagsetImport;
import de.catma.ui.module.project.documentwizard.TagsetImportState;
import de.catma.ui.module.project.documentwizard.UploadFile;
import de.catma.user.Member;
import de.catma.user.User;
import de.catma.util.CloseSafe;
import de.catma.util.IDGenerator;
import de.catma.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.vaadin.dialogs.ConfirmDialog;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * Renders a single project with all of its resources and members
 */
public class ProjectView extends HugeCard implements CanReloadAll {
	private final Logger logger = Logger.getLogger(ProjectView.class.getName());

	private final ProjectsManager projectsManager;
	private final EventBus eventBus;

	private final ErrorHandler errorHandler;
	private final TagManager tagManager;
	private final ProgressListener progressListener;

	private PropertyChangeListener projectExceptionListener;
	private PropertyChangeListener tagsetChangeListener;

	private ProjectReference projectReference;
	private Project project;
	private Map<String, Member> membersByIdentifier;

	private ProgressBar progressBar;

	// documents & annotations components
	private enum DocumentGridColumn {
		NAME,
		RESPONSIBLE,
	}
	private TreeGrid<Resource> documentGrid;
	private ActionGridComponent<TreeGrid<Resource>> documentGridComponent;
	private MenuItem miAddDocument;
	private MenuItem miAddCollection;
	private MenuItem miEditDocumentOrCollection;
	private MenuItem miDeleteDocumentOrCollection;
	private MenuItem miImportCollection;
	private MenuItem miToggleResponsibilityFilter;

	// tagsets components
	private enum TagsetGridColumn {
		NAME,
		RESPONSIBLE,
	}
	private Grid<TagsetDefinition> tagsetGrid;
	private ListDataProvider<TagsetDefinition> tagsetData;
	private ActionGridComponent<Grid<TagsetDefinition>> tagsetGridComponent;
	private MenuItem miEditTagset;
	private MenuItem miDeleteTaget;
	private MenuItem miImportTagset;

	// team components
	private VerticalFlexLayout teamLayout;
	private Grid<Member> teamGrid;

	// project components
	private LocalTime lastSynchronization;
	private Button btnSynchronize;
	private IconButton btnToggleViewSynchronizedOrLatestContributions;
	private MenuItem miCommit;
	private MenuItem miShareResources;
	private MenuItem miImportCorpus;
	private ProjectResourceExportApiDialog projectResourceExportApiDialog;

	public ProjectView(ProjectsManager projectsManager, EventBus eventBus) {
		super("Project");

		this.projectsManager = projectsManager;
		this.eventBus = eventBus;

		this.errorHandler = (ErrorHandler) UI.getCurrent();
		this.tagManager = new TagManager(new TagLibrary());

		final UI ui = UI.getCurrent();
		this.progressListener = new ProgressListener() {
			@Override
			public void setProgress(String value, Object... args) {
				ui.accessSynchronously(() -> {
					if (args != null) {
						progressBar.setCaption(String.format(value, args));
					}
					else {
						progressBar.setCaption(value);
					}
					ui.push();
				});
			}
		};

		initProjectListeners();
		initComponents();
		initActions();

		eventBus.register(this);
	}

	private void initProjectListeners() {
		projectExceptionListener = new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				Exception e = (Exception) evt.getNewValue();
				errorHandler.showAndLogError("Error in project", e);
			}
		};

		tagsetChangeListener = new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				handleTagsetChange(evt);
			}
		};
	}

	private void handleTagsetChange(PropertyChangeEvent evt) {
		Object oldValue = evt.getOldValue();
		Object newValue = evt.getNewValue();

		if (oldValue == null) { // creation
			tagsetData.refreshAll();
		}
		else if (newValue == null) { // removal
			tagsetGrid.deselect((TagsetDefinition) oldValue); // TODO: why bother if the data provider is about to be refreshed anyway?
			tagsetData.refreshAll();
		}
		else { // metadata update
			TagsetDefinition tagsetDefinition = (TagsetDefinition) newValue;
			tagsetData.refreshItem(tagsetDefinition);
		}
	}

	@Subscribe
	public void handleCollectionChanged(CollectionChangeEvent collectionChangeEvent) {
		if (!collectionChangeEvent.getChangeType().equals(ChangeType.CREATED)) {
			// ChangeType.UPDATED or DELETED
			// TODO: do we really need to re-init on updates or deletes?
			//       also see handleDocumentChanged & handleTagsetChange
			initData();
			return;
		}

		// ChangeType.CREATED
		SourceDocumentReference sourceDocumentRef = collectionChangeEvent.getDocument();
		AnnotationCollectionReference annotationCollectionRef = collectionChangeEvent.getCollectionReference();

		@SuppressWarnings("unchecked")
		TreeDataProvider<Resource> resourceDataProvider = (TreeDataProvider<Resource>) documentGrid.getDataProvider();

		DocumentResource documentResource = new DocumentResource(
				sourceDocumentRef,
				project.getId(),
				sourceDocumentRef.getResponsibleUser() == null ? null : membersByIdentifier.get(sourceDocumentRef.getResponsibleUser())
		);

		CollectionResource collectionResource = new CollectionResource(
				annotationCollectionRef,
				project.getId(),
				project.getCurrentUser()
		);

		resourceDataProvider.getTreeData().addItem(documentResource, collectionResource);
		resourceDataProvider.refreshAll();

		if (isAttached()) {
			documentGrid.expand(documentResource);

			Notification.show(
					"Info",
					String.format("Collection \"%s\" has been created", annotationCollectionRef.toString()),
					Notification.Type.TRAY_NOTIFICATION
			);
		}
	}

	private void setControlsStateBasedOnProjectReadOnlyState() {
		boolean controlsEnabled = !project.isReadOnly();

		miAddDocument.setEnabled(controlsEnabled);
		miAddCollection.setEnabled(controlsEnabled);
		miEditDocumentOrCollection.setEnabled(controlsEnabled);
		miDeleteDocumentOrCollection.setEnabled(controlsEnabled);
		miImportCollection.setEnabled(controlsEnabled);
		miEditTagset.setEnabled(controlsEnabled);
		miDeleteTaget.setEnabled(controlsEnabled);
		miImportTagset.setEnabled(controlsEnabled);
		miCommit.setEnabled(controlsEnabled);
		miShareResources.setEnabled(controlsEnabled);
		miImportCorpus.setEnabled(controlsEnabled);
		tagsetGridComponent.getActionGridBar().setAddBtnEnabled(controlsEnabled);
		btnSynchronize.setEnabled(controlsEnabled);
	}

	private void initActions() {
		// documents & annotations actions
		documentGridComponent.setSearchFilterProvider(searchInput -> createDocumentGridComponentSearchFilterProvider(searchInput));
		documentGrid.addItemClickListener(itemClickEvent -> handleResourceItemClick(itemClickEvent));

		ContextMenu documentGridComponentAddContextMenu = documentGridComponent.getActionGridBar().getBtnAddContextMenu();
		miAddDocument = documentGridComponentAddContextMenu.addItem("Add Document", menuItem -> handleAddDocumentRequest());
		miAddCollection = documentGridComponentAddContextMenu.addItem("Add Annotation Collection", menuItem -> handleAddCollectionRequest());

		ContextMenu documentGridComponentMoreOptionsContextMenu = documentGridComponent.getActionGridBar().getBtnMoreOptionsContextMenu();
		miEditDocumentOrCollection = documentGridComponentMoreOptionsContextMenu.addItem(
				"Edit Documents / Collections", menuItem -> handleEditResources()
		);
		miDeleteDocumentOrCollection = documentGridComponentMoreOptionsContextMenu.addItem(
				"Delete Documents / Collections", menuItem -> handleDeleteResources(documentGrid)
		);
		documentGridComponentMoreOptionsContextMenu.addItem(
				"Analyze Documents / Collections", menuItem -> handleAnalyzeResources(documentGrid)
		);
		miImportCollection = documentGridComponentMoreOptionsContextMenu.addItem(
				"Import a Collection", menuItem -> handleImportCollectionRequest()
		);

		MenuItem miExportDocumentsAndCollections = documentGridComponentMoreOptionsContextMenu.addItem(
				"Export Documents & Collections"
		);

		StreamResource documentsAndCollectionsExportStreamResource = new StreamResource(
				new CollectionXMLExportStreamSource(
					()-> getSelectedDocuments(),
					() -> documentGrid.getSelectedItems().stream().filter(resource -> resource.isCollection())
							.map(resource -> ((CollectionResource) resource).getCollectionReference())
							.collect(Collectors.toList()),
					() -> project
				),
				"CATMA-Corpus-Export-" + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + ".tar.gz"
		);
		documentsAndCollectionsExportStreamResource.setCacheTime(0);
		documentsAndCollectionsExportStreamResource.setMIMEType("application/gzip");

		FileDownloader documentsAndCollectionsExportFileDownloader = new FileDownloader(documentsAndCollectionsExportStreamResource);
		documentsAndCollectionsExportFileDownloader.extend(miExportDocumentsAndCollections);

		documentGridComponentMoreOptionsContextMenu.addItem(
				"Select Filtered Entries", menuItem -> handleSelectFilteredDocuments()
		);
		miToggleResponsibilityFilter = documentGridComponentMoreOptionsContextMenu.addItem(
				"Hide Others' Responsibilities", menuItem -> toggleResponsibilityFilter()
		);
		miToggleResponsibilityFilter.setCheckable(true);
		miToggleResponsibilityFilter.setChecked(false);

		// tagsets actions
		tagsetGridComponent.setSearchFilterProvider(searchInput -> createTagsetGridComponentSearchFilterProvider(searchInput));
		tagsetGrid.addItemClickListener(itemClickEvent -> handleTagsetClick(itemClickEvent));

		tagsetGridComponent.getActionGridBar().addBtnAddClickListener(clickEvent -> handleAddTagsetRequest());
   
		ContextMenu tagsetGridComponentMoreOptionsContextMenu = tagsetGridComponent.getActionGridBar().getBtnMoreOptionsContextMenu();
		miEditTagset = tagsetGridComponentMoreOptionsContextMenu.addItem(
				"Edit Tagset", menuItem -> handleEditTagsetRequest()
		);
		miDeleteTaget = tagsetGridComponentMoreOptionsContextMenu.addItem(
				"Delete Tagset", menuItem -> handleDeleteTagsetRequest()
		);
		miImportTagset = tagsetGridComponentMoreOptionsContextMenu.addItem(
				"Import Tagsets", menuItem -> handleImportTagsetsRequest()
		);
		MenuItem miExportTagsets = tagsetGridComponentMoreOptionsContextMenu.addItem("Export Tagsets");

		MenuItem miExportTagsetsAsXml = miExportTagsets.addItem("as XML");

		StreamResource tagsetsXmlExportStreamResource = new StreamResource(
				new TagsetXMLExportStreamSource(
						() -> tagsetGrid.getSelectedItems(),
						() -> project
				),
				"CATMA-Tagsets-Export-" + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + ".xml"
		);
		tagsetsXmlExportStreamResource.setCacheTime(0);
		tagsetsXmlExportStreamResource.setMIMEType("text/xml");

		FileDownloader tagsetsXmlExportFileDownloader = new FileDownloader(tagsetsXmlExportStreamResource);
		tagsetsXmlExportFileDownloader.extend(miExportTagsetsAsXml);

		MenuItem miExportTagsetsAsCsv = miExportTagsets.addItem("as CSV");

		StreamResource tagsetsCsvExportStreamResource = new StreamResource(
				new TagsetCSVExportStreamSource(
						() -> tagsetGrid.getSelectedItems(),
						() -> project
				),
				"CATMA-Tagsets-Export-" + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + ".csv"
		);
		tagsetsCsvExportStreamResource.setCacheTime(0);
		tagsetsCsvExportStreamResource.setMIMEType("text/comma-separated-values");

		FileDownloader tagsetsCsvExportFileDownloader =	new FileDownloader(tagsetsCsvExportStreamResource);
		tagsetsCsvExportFileDownloader.extend(miExportTagsetsAsCsv);

		// global project actions
		btnSynchronize.addClickListener(clickEvent -> handleSynchronize());
		btnToggleViewSynchronizedOrLatestContributions.addClickListener(clickEvent -> handleToggleViewSynchronizedOrLatestContributions());

		ContextMenu hugeCardMoreOptionsContextMenu = getMoreOptionsContextMenu();
		miCommit = hugeCardMoreOptionsContextMenu.addItem(
				"Commit All Changes", menuItem -> handleCommitRequest()
		);
		hugeCardMoreOptionsContextMenu.addSeparator();
		miShareResources = hugeCardMoreOptionsContextMenu.addItem(
				"Share Project Resources (Experimental API)", menuItem -> handleShareProjectResources()
		);
		miImportCorpus = hugeCardMoreOptionsContextMenu.addItem(
				"Import CATMA 5 Corpus", menuItem -> handleCorpusImport()
		);
		miImportCorpus.setVisible(
				CATMAPropertyKey.EXPERT_MODE.getBooleanValue() || Boolean.parseBoolean(
						((CatmaApplication) UI.getCurrent()).getParameter(Parameter.EXPERT, "false")
				)
		);
	}

	private void handleToggleViewSynchronizedOrLatestContributions() {
		// default is synchronized view, false = synchronized, true = latest contributions
		boolean isLatestContributionsViewCurrentlyEnabled = (Boolean) btnToggleViewSynchronizedOrLatestContributions.getData();

		// disable the entire layout
		setEnabled(false);

		try {
			if (isLatestContributionsViewCurrentlyEnabled) {
				setLatestContributionsView(false);
				return;
			}

			// switching to latest contributions view, check for uncommitted changes first
			if (!project.hasUncommittedChanges()) {
				setLatestContributionsView(true);
			}
			else {
				// there are uncommitted changes that need to be committed first
				SingleTextInputDialog dlg = new SingleTextInputDialog(
						"Commit All Changes",
						"You have changes that need to be committed first, please enter a short description for this commit:",
						commitMsg -> {
							try {
								project.commitAndPushChanges(commitMsg);
								setLatestContributionsView(true);
							}
							catch (Exception e) {
								errorHandler.showAndLogError("Failed to switch view", e);
								setEnabled(true);
							}
						}
				);
				dlg.show();
			}
		}
		catch (Exception e) {
			errorHandler.showAndLogError("Failed to switch view", e);
			setEnabled(true);
		}
	}

	private void setLatestContributionsView(final boolean enabled) throws Exception {
		// default is synchronized view, false = synchronized, true = latest contributions
		btnToggleViewSynchronizedOrLatestContributions.setData(enabled);
		btnToggleViewSynchronizedOrLatestContributions.setIcon(enabled ? VaadinIcons.RANDOM : VaadinIcons.ROAD_BRANCH);

		setProgressBarVisible(true);

		final UI ui = UI.getCurrent();

		project.setLatestContributionView(enabled, new OpenProjectListener() {
			@Override
			public void progress(String msg, Object... params) {
				ui.access(() -> {
					if (params != null) {
						progressBar.setCaption(String.format(msg, params));
					}
					else {
						progressBar.setCaption(msg);
					}
					ui.push();
				});
			}

			@Override
			public void ready(Project project) {
				setProgressBarVisible(false);
				reloadAll();
				setEnabled(true);
				eventBus.post(new HeaderContextChangeEvent(projectReference.getName(), enabled));
			}

			@Override
			public void failure(Throwable t) {
				setProgressBarVisible(false);
				setEnabled(true);
				errorHandler.showAndLogError("Failed to switch view", t);
			}
		});
	}

	private void toggleResponsibilityFilter() {
		documentGrid.getColumn(DocumentGridColumn.RESPONSIBLE.name()).setHidden(miToggleResponsibilityFilter.isChecked());
		documentGrid.getColumn(DocumentGridColumn.NAME.name()).setWidth(miToggleResponsibilityFilter.isChecked() ? 300 : 200);
		initData();
	}

	private void handleSynchronize() {
		if (
				lastSynchronization != null
				&& lastSynchronization.plus(
						CATMAPropertyKey.MIN_TIME_BETWEEN_SYNCHRONIZATIONS_SECONDS.getIntValue(),
						ChronoUnit.SECONDS
				).isAfter(LocalTime.now())
		) {
			Notification.show(
					"Info", 
					"You just synchronized a few seconds ago - please be patient, you can synchronize again in a few moments.",
					Notification.Type.HUMANIZED_MESSAGE
			);
			return;
		}

		lastSynchronization = LocalTime.now();
		synchronizeProject();
	}

	private void handleCorpusImport() {
		try {
			CorpusImportDialog corpusImportDialog = new CorpusImportDialog(
					new SaveCancelListener<Pair<File,List<CorpusImportDocumentMetadata>>>() {
						@Override
						public void savePressed(Pair<File, List<CorpusImportDocumentMetadata>> result) {
							importCorpus(result.getFirst(), result.getSecond());
						}
					}
			);

			if (!project.hasUncommittedChanges()) {
				corpusImportDialog.show();
			}
			else {
				SingleTextInputDialog dlg = new SingleTextInputDialog(
						"Commit All Changes",
						"You have changes that need to be committed first, please enter a short description for this commit:",
						commitMsg -> {
							try {
								project.commitAndPushChanges(commitMsg);
								corpusImportDialog.show();
							}
							catch (IOException e) {
								errorHandler.showAndLogError("Failed to import CATMA 5 corpus", e);
							}
						}
				);
				dlg.show();
			}
		}
		catch (Exception e) {
			errorHandler.showAndLogError("Failed to import CATMA 5 corpus", e);
		}
	}

	private void handleImportCorpusError(Throwable t) {
		setProgressBarVisible(false);
		setEnabled(true);

		errorHandler.showAndLogError(
				"Failed to import CATMA 5 corpus. The import operation has been aborted.",
				t
		);
	}

	private void importCorpus(final File corpusFile, final List<CorpusImportDocumentMetadata> documentMetadataList) {
		setEnabled(false);
		setProgressBarVisible(true);

		try {
			final UI ui = UI.getCurrent();
			final String tempDir = ((CatmaApplication) ui).acquirePersonalTempFolder();

			BackgroundServiceProvider backgroundServiceProvider = (BackgroundServiceProvider) ui;
			BackgroundService backgroundService = backgroundServiceProvider.acquireBackgroundService();

			backgroundService.submit(
				new DefaultProgressCallable<Void>() {
					@Override
					public Void call() throws Exception {
						return new CorpusImporter().importCorpus(
								getProgressListener(),
								corpusFile,
								documentMetadataList,
								tempDir,
								ui,
								project
						);
					}
				},
				new ExecutionListener<Void>() {
					@Override
					public void done(Void result) {
						setProgressBarVisible(false);
						setEnabled(true);
					}

					@Override
					public void error(Throwable t) {
						handleImportCorpusError(t);
					}
				},
				progressListener
			);
		}
		catch (IOException e) {
			handleImportCorpusError(e);
		}
	}

	private void handleImportCollectionRequest() {
		try {
			if (!project.hasUncommittedChanges()) {
				importCollection();
			}
			else {
				SingleTextInputDialog dlg = new SingleTextInputDialog(
						"Commit All Changes",
						"You have changes that need to be committed first, please enter a short description for this commit:",
						commitMsg -> {
							try {
								project.commitAndPushChanges(commitMsg);
								importCollection();
							}
							catch (IOException e) {
								errorHandler.showAndLogError("Failed to import collection", e);
							}
						}
				);
				dlg.show();
			}
		}
		catch (Exception e) {
			errorHandler.showAndLogError("Failed to import collection", e);
		}
	}

	private void importCollection() {
		Set<SourceDocumentReference> selectedSourceDocumentRefs = getSelectedDocuments();

		if (selectedSourceDocumentRefs.size() != 1) {
			Notification.show("Info", "Please select the corresponding document first!", Notification.Type.HUMANIZED_MESSAGE);
			return;
		}

		final SourceDocumentReference selectedSourceDocumentRef = selectedSourceDocumentRefs.iterator().next();

		GenericUploadDialog uploadDialog = new GenericUploadDialog(
				String.format("Upload a collection for \"%s\":", selectedSourceDocumentRef.toString()),
				new SaveCancelListener<byte[]>() {
					public void savePressed(byte[] result) {
						InputStream inputStream = new ByteArrayInputStream(result);

						try {
							if (BOMFilterInputStream.hasBOM(result)) {
								inputStream = new BOMFilterInputStream(inputStream, StandardCharsets.UTF_8);
							}

							Pair<AnnotationCollection, List<TagsetDefinitionImportStatus>> loadResult =
									project.prepareAnnotationCollectionForImport(inputStream, selectedSourceDocumentRef);

							final AnnotationCollection annotationCollection = loadResult.getFirst();
							List<TagsetDefinitionImportStatus> tagsetDefinitionImportStatuses = loadResult.getSecond();

							CollectionImportDialog importDialog = new CollectionImportDialog(
									tagsetDefinitionImportStatuses,
									new SaveCancelListener<List<TagsetDefinitionImportStatus>>() {
										@Override
										public void savePressed(List<TagsetDefinitionImportStatus> result) {
											try {
												project.importAnnotationCollection(result, annotationCollection);
											}
											catch (IOException e) {
												errorHandler.showAndLogError("Failed to import collection", e);
											}
										}
									}
							);

							importDialog.show();
						}
						catch (IOException e) {
							errorHandler.showAndLogError("Failed to import collection", e);
						}
						finally {
							CloseSafe.close(inputStream);
						}
					}
				}
		);

		uploadDialog.show();
	}

	private void handleTagsetClick(ItemClick<TagsetDefinition> itemClickEvent) {
		if (itemClickEvent.getMouseEventDetails().isDoubleClick()) {
			TagsetDefinition tagsetDefinition = itemClickEvent.getItem();
			eventBus.post(new RouteToTagsEvent(project, tagsetDefinition));
		}
	}

	private void handleSelectFilteredDocuments() {
		documentGridComponent.setSelectionMode(SelectionMode.MULTI);

		@SuppressWarnings("unchecked")
		TreeDataProvider<Resource> dataProvider = (TreeDataProvider<Resource>) documentGrid.getDataProvider();
		dataProvider.fetch(new HierarchicalQuery<>(dataProvider.getFilter(), null))
				.forEach(resource -> {
					documentGrid.select(resource);
					dataProvider.fetch(new HierarchicalQuery<>(dataProvider.getFilter(), resource))
							.forEach(child -> documentGrid.select(child));
				});
	}

	private void handleImportTagsetsRequest() {
		GenericUploadDialog uploadDialog = new GenericUploadDialog(
				"Upload a tag library with one or more tagsets:",
				new SaveCancelListener<byte[]>() {
					public void savePressed(byte[] result) {
						InputStream is = new ByteArrayInputStream(result);

						try {
							if (BOMFilterInputStream.hasBOM(result)) {
								is = new BOMFilterInputStream(is, StandardCharsets.UTF_8);
							}

							List<TagsetDefinitionImportStatus> tagsetDefinitionImportStatuses = project.prepareTagLibraryForImport(is);

							TagsetImportDialog tagsetImportDialog = new TagsetImportDialog(
									tagsetDefinitionImportStatuses,
									new SaveCancelListener<List<TagsetDefinitionImportStatus>>() {
										@Override
										public void savePressed(List<TagsetDefinitionImportStatus> result) {
											try {
												project.importTagsets(result);
											}
											catch (IOException e) {
												errorHandler.showAndLogError("Failed to import tagset(s)", e);
											}
										}
									}
							);

							tagsetImportDialog.show();
						}
						catch (IOException e) {
							errorHandler.showAndLogError("Failed to import tagset(s)", e);
						}
						finally {
							CloseSafe.close(is);
						}
					}
				}
		);

		uploadDialog.show();
	}

	private void synchronizeProject() {
		setProgressBarVisible(true);
		setEnabled(false);

		final UI ui = UI.getCurrent();

		try {
			project.synchronizeWithRemote(new OpenProjectListener() {
				@Override
				public void progress(String msg, Object... params) {
					ui.access(() -> {
						if (params != null) {
							progressBar.setCaption(String.format(msg, params));
						}
						else {
							progressBar.setCaption(msg);
						}
						ui.push();
					});
				}

				@Override
				public void ready(Project project) {
					setProgressBarVisible(false);
					reloadAll();
					setEnabled(true);

					if (project == null) {
						Notification syncFailedNotification = new Notification(
								"Your project cannot be synchronized right now.\n" +
										"Try again later or check the CATMA GitLab backend for open merge requests\n" +
										"that may require manual conflict resolution. (click to dismiss)",
								Notification.Type.WARNING_MESSAGE
						);
						syncFailedNotification.setDelayMsec(-1);
						syncFailedNotification.show(Page.getCurrent());
						return;
					}

					Notification.show("Info", "Your project has been synchronized", Notification.Type.HUMANIZED_MESSAGE);
				}

				@Override
				public void failure(Throwable t) {
					setProgressBarVisible(false);
					setEnabled(true);

					errorHandler.showAndLogError("Failed to synchronize or re-open project. Please contact support.", t);
				}
			});
		}
		catch (Exception e) {
			setProgressBarVisible(false);
			setEnabled(true);

			errorHandler.showAndLogError("Failed to synchronize project. Please contact support.", e);
		}
	}

	private void setProgressBarVisible(boolean visible) {
		progressBar.setIndeterminate(visible);
		progressBar.setVisible(visible);
		if (!visible) {
			progressBar.setCaption("");
		}
	}

	private void handleEditCollection(Resource collectionToEdit) {
		final AnnotationCollectionReference collectionRef = ((CollectionResource) collectionToEdit).getCollectionReference();
		boolean isBeyondCurrentUsersResponsibility = !collectionRef.isResponsible(project.getCurrentUser().getIdentifier());

		try {
			BeyondResponsibilityConfirmDialog.executeWithConfirmDialog(
					isBeyondCurrentUsersResponsibility,
					project.hasPermission(project.getCurrentUserProjectRole(), RBACPermission.COLLECTION_DELETE_OR_EDIT),
					new Action() {
						@Override
						public void execute() {
							EditResourceDialog editCollectionDialog = new EditResourceDialog(
									"Edit Collection Metadata",
									collectionRef.getContentInfoSet(),
									collectionRef.getResponsibleUser(),
									membersByIdentifier.values(),
									new SaveCancelListener<Pair<String, ContentInfoSet>>() {
										@Override
										public void savePressed(Pair<String, ContentInfoSet> result) {
											try {
												String updatedResponsibleUser = result.getFirst();
												collectionRef.setResponsibleUser(updatedResponsibleUser);
												// the ContentInfoSet is updated directly by EditResourceDialog

												project.updateAnnotationCollectionMetadata(collectionRef);
											}
											catch (IOException e) {
												errorHandler.showAndLogError(
														String.format("Failed to update collection \"%s\"", collectionRef.getName()),
														e
												);
												// TODO: why are we doing this here?
												//       (at all, and not for documents in handleEditDocument or tagsets in handleEditTagsetRequest)
												reloadAll();
											}
										}
									}
							);

							editCollectionDialog.show();
						}
					}
			);
		}
		catch (IOException e) {
			errorHandler.showAndLogError(
					String.format("Failed to update collection \"%s\"", collectionRef.getName()),
					e
			);
		}
	}

	private void handleEditDocument(Resource documentToEdit) {
		final SourceDocumentReference documentRef = ((DocumentResource) documentToEdit).getDocument();
		boolean isBeyondCurrentUsersResponsibility = !documentRef.isResponsible(project.getCurrentUser().getIdentifier());

		try {
			BeyondResponsibilityConfirmDialog.executeWithConfirmDialog(
					isBeyondCurrentUsersResponsibility,
					project.hasPermission(project.getCurrentUserProjectRole(), RBACPermission.DOCUMENT_DELETE_OR_EDIT),
					new Action() {
						@Override
						public void execute() {
							EditResourceDialog editDocumentDialog = new EditResourceDialog(
									"Edit Document Metadata",
									documentRef.getSourceDocumentInfo().getContentInfoSet(),
									documentRef.getSourceDocumentInfo().getTechInfoSet().getResponsibleUser(),
									membersByIdentifier.values(),
									new SaveCancelListener<Pair<String, ContentInfoSet>>() {
										@Override
										public void savePressed(Pair<String, ContentInfoSet> result) {
											try {
												String updatedResponsibleUser = result.getFirst();
												documentRef.setResponsibleUser(updatedResponsibleUser);
												// the ContentInfoSet is updated directly by EditResourceDialog

												project.updateSourceDocumentMetadata(documentRef);
											}
											catch (IOException e) {
												errorHandler.showAndLogError(
														String.format(
																"Failed to update document \"%s\"",
																documentRef.getSourceDocumentInfo().getContentInfoSet().getTitle()
														),
														e
												);
											}
										}
									}
							);

							editDocumentDialog.show();
						}
					}
			);
		}
		catch (IOException e) {
			errorHandler.showAndLogError(
					String.format("Failed to update document \"%s\"", documentRef.getSourceDocumentInfo().getContentInfoSet().getTitle()),
					e
			);
		}
	}

	private void handleEditResources() {
		final Set<Resource> selectedResources = documentGrid.getSelectedItems();

		if (selectedResources.isEmpty()) {
			Notification.show("Info", "Please select a resource first!", Notification.Type.HUMANIZED_MESSAGE);
			return;
		}

		// TODO: this silently ignores all but the first selected resource - disallow multi-select?
		// only one resource can be edited at a time
		if (selectedResources.size() > 1) {
			documentGridComponent.setSelectionMode(SelectionMode.SINGLE);
		}
		// take the first selected resource
		final Resource resourceToEdit = selectedResources.iterator().next();

		if (resourceToEdit.isCollection()) {
			handleEditCollection(resourceToEdit);
		}
		else { // document
			handleEditDocument(resourceToEdit);
		}
	}

	private void handleDeleteTagsetRequest() {
		final Set<TagsetDefinition> selectedTagsets = tagsetGrid.getSelectedItems();

		if (selectedTagsets.isEmpty()) {
			Notification.show("Info", "Please select one or more tagsets first!", Notification.Type.HUMANIZED_MESSAGE);
			return;
		}

		boolean isBeyondCurrentUsersResponsibility = selectedTagsets.stream().anyMatch(
				tagset -> !tagset.isResponsible(project.getCurrentUser().getIdentifier())
		);

		List<String> selectedTagsetNames = selectedTagsets.stream()
				.map(TagsetDefinition::getName)
				.sorted()
				.collect(Collectors.toList());
		String quotedCommaSeparatedTagsetNames = String.format("\"%s\"", String.join("\", \"", selectedTagsetNames));

		try {
			BeyondResponsibilityConfirmDialog.executeWithConfirmDialog(
					isBeyondCurrentUsersResponsibility,
					project.hasPermission(project.getCurrentUserProjectRole(), RBACPermission.TAGSET_DELETE_OR_EDIT),
					new Action() {
						@Override
						public void execute() {
							ConfirmDialog.show(
									UI.getCurrent(),
									"Warning",
									String.format(
											"Are you sure you want to delete tagset(s) %s and all related data?",
											quotedCommaSeparatedTagsetNames
									),
									"Delete",
									"Cancel",
									dlg -> {
										if (dlg.isConfirmed()) {
											try {
												for (TagsetDefinition tagset : selectedTagsets) {
													project.getTagManager().removeTagsetDefinition(tagset);
												}
											}
											catch (Exception e) {
												errorHandler.showAndLogError(
														String.format("Failed to delete tagset(s) %s", quotedCommaSeparatedTagsetNames),
														e
												);
											}
										}
									}
							);
						}
					}
			);
		}
		catch (IOException e) {
			errorHandler.showAndLogError(
					String.format("Failed to delete tagset(s) %s", quotedCommaSeparatedTagsetNames),
					e
			);
		}
	}

	private void handleEditTagsetRequest() {
		final Set<TagsetDefinition> selectedTagsets = tagsetGrid.getSelectedItems();

		if (selectedTagsets.isEmpty()) {
			Notification.show("Info", "Please select a tagset first!", Notification.Type.HUMANIZED_MESSAGE);
			return;
		}

		// TODO: this silently ignores all but the first selected tagset - disallow multi-select?
		// only one tagset can be edited at a time
		if (selectedTagsets.size() > 1) {
			tagsetGrid.setSelectionMode(SelectionMode.SINGLE);
		}
		// take the first selected tagset
		final TagsetDefinition tagsetToEdit = selectedTagsets.iterator().next();

		boolean isBeyondCurrentUsersResponsibility = !tagsetToEdit.isResponsible(project.getCurrentUser().getIdentifier());

		try {
			BeyondResponsibilityConfirmDialog.executeWithConfirmDialog(
					isBeyondCurrentUsersResponsibility,
					project.hasPermission(project.getCurrentUserProjectRole(), RBACPermission.TAGSET_DELETE_OR_EDIT),
					new Action() {
						@Override
						public void execute() {
							EditTagsetDialog editTagsetDialog = new EditTagsetDialog(
									new TagsetMetadata(tagsetToEdit.getName(), tagsetToEdit.getDescription(), tagsetToEdit.getResponsibleUser()),
									membersByIdentifier.values(),
									new SaveCancelListener<TagsetMetadata>() {
									@Override
									public void savePressed(TagsetMetadata result) {
										try {
											project.getTagManager().setTagsetMetadata(tagsetToEdit, result);
										}
										catch (Exception e) {
											errorHandler.showAndLogError(
													String.format("Failed to update tagset \"%s\"", tagsetToEdit.getName()),
													e
											);
										}
									}
								}
							);

							editTagsetDialog.show();
						}
					}
			);
		}
		catch (IOException e) {
			errorHandler.showAndLogError(
					String.format("Failed to update tagset \"%s\"", tagsetToEdit.getName()),
					e
			);
		}
	}

	private void handleAddTagsetRequest() {
		SingleTextInputDialog tagsetNameDlg = new SingleTextInputDialog(
				"Create Tagset",
				"Please enter the tagset name:",
				new SaveCancelListener<String>() {
					@Override
					public void savePressed(String result) {
							IDGenerator idGenerator = new IDGenerator();
							TagsetDefinition tagsetDefinition = new TagsetDefinition(idGenerator.generateTagsetId(), result);
							tagsetDefinition.setResponsibleUser(project.getCurrentUser().getIdentifier());
							project.getTagManager().addTagsetDefinition(tagsetDefinition);
					}
				}
		);

		tagsetNameDlg.show();
	}

	private Set<SourceDocumentReference> getSelectedDocuments() {
		Set<Resource> selectedResources = documentGrid.getSelectedItems();

		Set<SourceDocumentReference> selectedSourceDocumentRefs = new HashSet<>();

		@SuppressWarnings("unchecked")
		TreeDataProvider<Resource> resourceDataProvider = (TreeDataProvider<Resource>) documentGrid.getDataProvider();

		for (Resource resource : selectedResources) {
			Resource root = resourceDataProvider.getTreeData().getParent(resource);

			if (root == null) {
				root = resource;
			}

			DocumentResource documentResource = (DocumentResource) root;
			selectedSourceDocumentRefs.add(documentResource.getDocument());
		}

		return selectedSourceDocumentRefs;
	}

	private void handleAddCollectionRequest() {
		Set<SourceDocumentReference> selectedSourceDocumentRefs = getSelectedDocuments();

		if (selectedSourceDocumentRefs.isEmpty()) {
			Notification.show("Info", "Please select one or more documents first!", Notification.Type.HUMANIZED_MESSAGE);
			return;
		}

		try {
			SingleTextInputDialog collectionNameDialog = new SingleTextInputDialog(
					"Create Annotation Collection(s)",
					"Please enter the collection name:",
					new SaveCancelListener<String>() {
						@Override
						public void savePressed(String result) {
							try {
								for (SourceDocumentReference sourceDocumentRef : selectedSourceDocumentRefs) {
									project.createAnnotationCollection(result, sourceDocumentRef);
								}
							}
							catch (Exception e) {
								errorHandler.showAndLogError(
										String.format("Failed to create collection \"%s\"", result),
										e
								);
							}
						}
					}
			);

			collectionNameDialog.show();
		}
		catch (Exception e) {
			errorHandler.showAndLogError("Failed to create collection", e);
		}
	}

	private void handleAddDocumentRequest() {
		WizardContext wizardContext = new WizardContext();
		wizardContext.put(DocumentWizard.WizardContextKey.PROJECT, project);

		DocumentWizard documentWizard = new DocumentWizard(
				wizardContext,
				new SaveCancelListener<WizardContext>() {
					@Override
					public void savePressed(WizardContext result) {
						handleSaveDocumentWizardContext(result);
					}

				}
		);

		documentWizard.show();
	}

	private void handleSaveDocumentWizardContext(final WizardContext result) {
		setEnabled(false);
		setProgressBarVisible(true);

		final UI ui = UI.getCurrent();

		BackgroundServiceProvider backgroundServiceProvider = (BackgroundServiceProvider) ui;
		BackgroundService backgroundService = backgroundServiceProvider.acquireBackgroundService();

		backgroundService.submit(
			new DefaultProgressCallable<Void>() {
				@SuppressWarnings("unchecked")
				@Override
				public Void call() throws Exception {
					Collection<TagsetImport> tagsetImports = (Collection<TagsetImport>) result.get(DocumentWizard.WizardContextKey.TAGSET_IMPORT_LIST);
					Collection<UploadFile> uploadFiles = (Collection<UploadFile>) result.get(DocumentWizard.WizardContextKey.UPLOAD_FILE_LIST);

					if (tagsetImports == null) {
						tagsetImports = Collections.emptyList();
					}

					// ignoring tagsets
					// uploaded files may contain tagsets and annotations (TEI-XML)
					// filter out those annotations that use tags that belong to tagsets which were not selected for import
					tagsetImports.stream()
							.filter(ti -> ti.getImportState().equals(TagsetImportState.WILL_BE_IGNORED))
							.map(TagsetImport::getExtractedTagset)
							.forEach(ignoredTagsetDefinition -> uploadFiles.stream()
									.filter(uploadFile -> uploadFile.getIntrinsicMarkupCollection() != null)
									.forEach(uploadFile -> {
										AnnotationCollection intrinsicAnnotationCollection = uploadFile.getIntrinsicMarkupCollection();
										intrinsicAnnotationCollection.removeTagReferences(
												intrinsicAnnotationCollection.getTagReferences(ignoredTagsetDefinition)
										);
									})
							);

					getProgressListener().setProgress("Importing tagsets");

					// creating tagsets
					tagsetImports.stream()
							.filter(ti -> ti.getImportState().equals(TagsetImportState.WILL_BE_CREATED))
							.forEach(tagsetImport -> {
								getProgressListener().setProgress("Importing tagset \"%s\"", tagsetImport.getTargetTagset().getName());

								ui.accessSynchronously(() -> {
									if (project.getTagManager().getTagLibrary().getTagsetDefinition(tagsetImport.getTargetTagset().getUuid()) != null) {
										// tagset already exists in project, so it will be a merge (handled below)
										tagsetImport.setImportState(TagsetImportState.WILL_BE_MERGED);
									}
									else {
										TagsetDefinition extractedTagset = tagsetImport.getExtractedTagset();

										try {
											project.importTagsets(Collections.singletonList(
													new TagsetDefinitionImportStatus(
															extractedTagset,
															project.getTagManager().getTagLibrary().getTagsetDefinition(
																	extractedTagset.getUuid()
															) != null
													)
											));
										}
										catch (Exception e) {
											logger.log(
													Level.SEVERE,
													String.format(
															"Failed to import tagset \"%s\" with ID %s",
															extractedTagset.getName(),
															extractedTagset.getUuid()
													),
													e
											);

											Notification.show(
													"Error",
													String.format(
															"Failed to import tagset \"%s\"! This tagset will be skipped.\n" +
																	"The underlying error message was:\n%s",
															extractedTagset.getName(),
															e.getMessage()
													),
													Notification.Type.ERROR_MESSAGE
											);
										}
									}

									ui.push();
								});
							});

					// merging tagsets
					tagsetImports.stream()
							.filter(ti -> ti.getImportState().equals(TagsetImportState.WILL_BE_MERGED))
							.forEach(tagsetImport -> {
								getProgressListener().setProgress("Merging tagset \"%s\"", tagsetImport.getTargetTagset().getName());

								ui.accessSynchronously(() -> {
									TagsetDefinition targetTagset = project.getTagManager().getTagLibrary().getTagsetDefinition(
											tagsetImport.getTargetTagset().getUuid()
									);

									IDGenerator idGenerator = new IDGenerator();

									for (TagDefinition incomingTagDefinition : tagsetImport.getExtractedTagset()) {
										Optional<TagDefinition> optionalExistingTagDefinition = targetTagset.getTagDefinitionsByName(
												incomingTagDefinition.getName()
										).findFirst();

										if (!optionalExistingTagDefinition.isPresent()) {
											// tag doesn't exist in target tagset, add it
											incomingTagDefinition.setTagsetDefinitionUuid(targetTagset.getUuid());
											project.getTagManager().addTagDefinition(targetTagset, incomingTagDefinition);
											continue;
										}

										// otherwise, tag *does* exist in target tagset...
										TagDefinition existingTagDefinition = optionalExistingTagDefinition.get();

										// add any missing properties to the existing tag...
										incomingTagDefinition.getUserDefinedPropertyDefinitions().forEach(incomingPropertyDefinition -> {
											if (existingTagDefinition.getPropertyDefinition(incomingPropertyDefinition.getName()) == null) {
												project.getTagManager().addUserDefinedPropertyDefinition(
														existingTagDefinition, new PropertyDefinition(incomingPropertyDefinition)
												);
											}
										});

										// then, import tag instances (** but assign new IDs)
										uploadFiles.stream()
												.filter(uploadFile -> uploadFile.getIntrinsicMarkupCollection() != null)
												.forEach(uploadFile -> {
													AnnotationCollection intrinsicAnnotationCollection = uploadFile.getIntrinsicMarkupCollection();
													List<TagReference> incomingTagReferences = intrinsicAnnotationCollection.getTagReferences(
															incomingTagDefinition
													);
													intrinsicAnnotationCollection.removeTagReferences(incomingTagReferences);

													Multimap<TagInstance, TagReference> incomingTagReferencesByTagInstance = ArrayListMultimap.create();
													incomingTagReferences.forEach(incomingTagReference ->
															incomingTagReferencesByTagInstance.put(incomingTagReference.getTagInstance(), incomingTagReference)
													);

													for (TagInstance incomingTagInstance : incomingTagReferencesByTagInstance.keySet()) {
														TagInstance newTagInstance = new TagInstance(
																idGenerator.generate(), // ** generate new ID
																existingTagDefinition.getUuid(),
																incomingTagInstance.getAuthor(),
																incomingTagInstance.getTimestamp(),
																existingTagDefinition.getUserDefinedPropertyDefinitions(),
																targetTagset.getUuid()
														);

														// for existing properties, we keep the existing property definition
														// but take the incoming property values
														for (Property incomingProperty : incomingTagInstance.getUserDefinedProperties()) {
															PropertyDefinition incomingPropertyDefinition =
																	incomingTagDefinition.getPropertyDefinitionByUuid(
																			incomingProperty.getPropertyDefinitionId()
																	);

															PropertyDefinition existingPropertyDefinition =
																	existingTagDefinition.getPropertyDefinition(incomingPropertyDefinition.getName());

															newTagInstance.addUserDefinedProperty(
																	new Property(
																			existingPropertyDefinition.getUuid(),
																			incomingProperty.getPropertyValueList()
																	)
															);
														}

														// re-write tag references
														ArrayList<TagReference> newTagReferences = new ArrayList<>();

														incomingTagReferencesByTagInstance.get(incomingTagInstance).forEach(incomingTagReference -> {
															try {
																newTagReferences.add(new TagReference(
																		newTagInstance,
																		incomingTagReference.getTarget().toString(),
																		incomingTagReference.getRange(),
																		incomingTagReference.getUserMarkupCollectionUuid()
																));
															}
															catch (URISyntaxException e) {
																// shouldn't ever happen as we get the URI string from an existing URI object
																e.printStackTrace();
															}
														});

														intrinsicAnnotationCollection.addTagReferences(newTagReferences);
													}
												});
									}

									ui.push();
								});
							});

					// creating documents and collections
					boolean useApostropheAsSeparator = (Boolean) result.get(DocumentWizard.WizardContextKey.APOSTROPHE_AS_SEPARATOR);
					String collectionNamePattern = (String) result.get(DocumentWizard.WizardContextKey.COLLECTION_NAME_PATTERN);

					for (UploadFile uploadFile : uploadFiles) {
						getProgressListener().setProgress("Importing document \"%s\"", uploadFile.getTitle());

						ui.accessSynchronously(() -> {
							addUploadFile(uploadFile, useApostropheAsSeparator, collectionNamePattern);
							ui.push();
						});
					}

					return null;
				}
			},
			new ExecutionListener<Void>() {
				@Override
				public void done(Void result) {
					setProgressBarVisible(false);
					setEnabled(true);
				}

				@Override
				public void error(Throwable t) {
					setProgressBarVisible(false);
					setEnabled(true);
					errorHandler.showAndLogError("Failed to add documents", t);
				}
			},
			progressListener
		);
	}

	private void addUploadFile(UploadFile uploadFile, boolean useApostropheAsSeparator, String collectionNamePattern) {
		SourceDocumentInfo sourceDocumentInfo = new SourceDocumentInfo(
				uploadFile.getIndexInfoSet(useApostropheAsSeparator),
				uploadFile.getContentInfoSet(),
				uploadFile.getTechInfoSet()
		);

		SourceContentHandler sourceContentHandler =
				sourceDocumentInfo.getTechInfoSet().getMimeType().equals(FileType.XML2.getMimeType()) ? new XML2ContentHandler() : new TikaContentHandler();
		sourceContentHandler.setSourceDocumentInfo(sourceDocumentInfo);

		SourceDocument sourceDocument = new SourceDocument(uploadFile.getUuid(), sourceContentHandler);

		try {
			String documentContent = sourceDocument.getContent();

			sourceDocumentInfo.getTechInfoSet().setFileOSType(FileOSType.getFileOSType(documentContent));

			CRC32 checksum = new CRC32();
			checksum.update(documentContent.getBytes());
			sourceDocumentInfo.getTechInfoSet().setChecksum(checksum.getValue());

			project.addSourceDocument(sourceDocument);

			AnnotationCollection intrinsicAnnotationCollection = uploadFile.getIntrinsicMarkupCollection();
			if (intrinsicAnnotationCollection != null) {
				project.importAnnotationCollection(Collections.emptyList(), intrinsicAnnotationCollection);
			}

			if (!StringUtils.isBlank(collectionNamePattern)) {
				String collectionName = collectionNamePattern.replace("{{Title}}", uploadFile.getTitle());
				project.createAnnotationCollection(collectionName, project.getSourceDocumentReference(sourceDocument.getUuid()));
			}
		}
		catch (Exception e) {
			logger.log(
					Level.SEVERE, 
					String.format("Failed to load document content from file %s", uploadFile.getTempFilename().toString()),
					e
			);

			Notification.show(
					"Error",
					String.format(
						"Failed to load document content from file \"%s\"! This document will be skipped.\n" +
								"The underlying error message was:\n%s",
						uploadFile.getTitle(),
						e.getMessage()
					),
					Notification.Type.ERROR_MESSAGE
			);
		}
	}

	private void handleResourceItemClick(ItemClick<Resource> itemClickEvent) {
		if (!itemClickEvent.getMouseEventDetails().isDoubleClick()) {
			return;
		}

		Resource resource = itemClickEvent.getItem();

		@SuppressWarnings("unchecked")
		TreeDataProvider<Resource> resourceDataProvider = (TreeDataProvider<Resource>) documentGrid.getDataProvider();

		Resource root = resourceDataProvider.getTreeData().getParent(resource);
		Resource child = null;

		if (root == null) {
			root = resource;
		}
		else {
			child = resource;
		}

		if (root != null) {
			SourceDocumentReference sourceDocumentRef = ((DocumentResource) root).getDocument();
			AnnotationCollectionReference annotationCollectionRef = child == null ? null : ((CollectionResource) child).getCollectionReference();

			eventBus.post(new RouteToAnnotateEvent(project, sourceDocumentRef, annotationCollectionRef));
		}
	}

	private void initComponents() {
		progressBar = new ProgressBar();
		progressBar.setIndeterminate(false);
		progressBar.setVisible(false);
		addComponent(progressBar);
		setComponentAlignment(progressBar, Alignment.TOP_CENTER);

		HorizontalFlexLayout mainLayout = new HorizontalFlexLayout();
		mainLayout.setFlexWrap(FlexWrap.WRAP);
		mainLayout.addStyleName("project-view-main-panel");

		VerticalFlexLayout resourcesLayout = new VerticalFlexLayout();
		resourcesLayout.setSizeUndefined(); // don't set width 100%
		resourcesLayout.addComponent(new Label("Resources"));

		resourcesLayout.addComponent(initResourcesContent());

		mainLayout.addComponent(resourcesLayout);

		teamLayout = new VerticalFlexLayout();
		teamLayout.setSizeUndefined(); // don't set width 100%
		teamLayout.setVisible(false);
		teamLayout.addComponent(new Label("Team"));

		teamLayout.addComponent(initTeamContent());

		mainLayout.addComponent(teamLayout);

		addComponent(mainLayout);
		setExpandRatio(mainLayout, 1.f);

		btnSynchronize = new IconButton(VaadinIcons.SHARE);
		btnSynchronize.setDescription("Synchronize with the Team");
		getHugeCardBar().addComponentBeforeMoreOptions(btnSynchronize);

		btnToggleViewSynchronizedOrLatestContributions = new IconButton(VaadinIcons.ROAD_BRANCH);
		btnToggleViewSynchronizedOrLatestContributions.setData(false); // default is synchronized view, false = synchronized, true = latest contributions
		btnToggleViewSynchronizedOrLatestContributions.setDescription("Switch between 'Synchronized' view and 'Latest Contributions' view");
		getHugeCardBar().addComponentBeforeMoreOptions(btnToggleViewSynchronizedOrLatestContributions);
	}

	private void handleProjectInvitationRequest() {
		@SuppressWarnings("unchecked")
		TreeDataProvider<Resource> resourceDataProvider = (TreeDataProvider<Resource>) documentGrid.getDataProvider();

		List<DocumentResource> documentResources = resourceDataProvider.getTreeData().getRootItems()
				.stream()
				.filter(resource -> resource instanceof DocumentResource)
				.map(resource -> (DocumentResource) resource)
			 	.collect(Collectors.toList());

		new ProjectInvitationDialog(
				project,
				documentResources,
				eventBus,
				((CatmaApplication) UI.getCurrent()).getHazelCastService()
		).show();
	}

	private void handleCommitRequest() {
		try {
			if (!project.hasUncommittedChanges()) {
				Notification.show("Info", "There are no uncommitted changes", Notification.Type.HUMANIZED_MESSAGE);
				return;
			}
		}
		catch (Exception e) {
			errorHandler.showAndLogError("Failed to check for uncommitted changes", e);
			return;
		}

		SingleTextInputDialog dlg = new SingleTextInputDialog(
				"Commit All Changes",
				"Please enter a short description for this commit:",
				commitMsg -> {
					try {
						project.commitAndPushChanges(commitMsg);
						Notification.show("Info", "Your changes have been committed", Notification.Type.HUMANIZED_MESSAGE);
					}
					catch (IOException e) {
						errorHandler.showAndLogError("Failed to commit changes", e);
					}
				}
		);
		dlg.show();
	}

	private final Function<Resource, String> buildResourceNameHtml = (resource) -> {
		StringBuilder sb = new StringBuilder()
				.append("<div class='documentsgrid__doc'>")
				.append("<div class='documentsgrid__doc__title")
				.append(resource.isContribution() ? " documentsgrid__doc__contrib'>" : "'>")
				.append(resource.getName())
				.append("</div>");

		if (resource.hasDetail()) {
			sb.append("<span class='documentsgrid__doc__author'>")
					.append(resource.getDetail())
					.append("</span>");
		}

		sb.append("</div>");

		return sb.toString();
	};

	private final Function<Resource, String> buildResourceResponsibilityHtml = (resource) -> {
		if (resource.getResponsibleUser() == null) {
			return "";
		}

		return String.format("<div class='documentsgrid__doc'>%s</div>", resource.getResponsibleUser());
	};

	private Component initResourcesContent() {
		HorizontalFlexLayout resourcesContentLayout = new HorizontalFlexLayout();

		documentGrid = TreeGridFactory.createDefaultTreeGrid();
		documentGrid.addStyleNames("flat-undecorated-icon-buttonrenderer");
		documentGrid.setHeaderVisible(false);
		documentGrid.setRowHeight(45);

		documentGrid.addColumn(Resource::getIcon, new HtmlRenderer())
				.setWidth(100);

		documentGrid.addColumn(buildResourceNameHtml::apply, new HtmlRenderer())
				.setId(DocumentGridColumn.NAME.name())
				.setCaption("Name");

		documentGrid.addColumn(buildResourceResponsibilityHtml::apply, new HtmlRenderer())
				.setId(DocumentGridColumn.RESPONSIBLE.name())
				.setCaption("Responsible")
				.setExpandRatio(1)
				.setHidden(false);

		documentGridComponent = new ActionGridComponent<>(
				new Label("Documents & Annotations"),
				documentGrid
		);
		documentGridComponent.addStyleName("project-view-action-grid");

		resourcesContentLayout.addComponent(documentGridComponent);

		tagsetGrid = new Grid<>();
		tagsetGrid.setHeaderVisible(false);
		tagsetGrid.setWidth("400px");

		tagsetGrid.addColumn(tagsetDefinition -> VaadinIcons.TAGS.getHtml(), new HtmlRenderer())
				.setWidth(100);

		tagsetGrid.addColumn(TagsetDefinition::getName)
				.setId(TagsetGridColumn.NAME.name())
				.setCaption("Name")
				.setStyleGenerator(tagsetDefinition -> tagsetDefinition.isContribution() ? "project-view-tagset-with-contribution" : null);

		tagsetGrid.addColumn(
				tagsetDefinition -> tagsetDefinition.getResponsibleUser() == null ?
						"Not assigned" : membersByIdentifier.get(tagsetDefinition.getResponsibleUser())
				)
				.setId(TagsetGridColumn.RESPONSIBLE.name())
				.setCaption("Responsible")
				.setExpandRatio(1)
				.setHidden(true)
				.setHidable(true);

		tagsetGridComponent = new ActionGridComponent<>(
				new Label("Tagsets"),
				tagsetGrid
		);
		tagsetGridComponent.addStyleName("project-view-action-grid");

		resourcesContentLayout.addComponent(tagsetGridComponent);

		return resourcesContentLayout;
	}

	private Component initTeamContent() {
		HorizontalFlexLayout teamContent = new HorizontalFlexLayout();

		teamGrid = new Grid<>();
		teamGrid.setHeaderVisible(false);
		teamGrid.setWidth("402px");
		teamGrid.addColumn((user) -> VaadinIcons.USER.getHtml(), new HtmlRenderer());
		teamGrid.addColumn(User::getName)
				.setWidth(200)
				.setComparator((r1, r2) -> String.CASE_INSENSITIVE_ORDER.compare(r1.getName(), r2.getName()))
				.setDescriptionGenerator(User::preciseName);
		teamGrid.addColumn(Member::getRole).setExpandRatio(1);

		ActionGridComponent<Grid<Member>> membersGridComponent = new ActionGridComponent<>(
				new Label("Members"),
				teamGrid
		);
		membersGridComponent.addStyleName("project-view-action-grid");

		ContextMenu addContextMenu = membersGridComponent.getActionGridBar().getBtnAddContextMenu();
		addContextMenu.addItem(
				"Add Member",
				(selectedItem) -> new AddMemberDialog(
						project::assignRoleToSubject,
						(query) -> project.findUser(query.getFilter().isPresent() ? query.getFilter().get() : ""),
						(evt) -> eventBus.post(new MembersChangedEvent())
				).show()
		);

		ContextMenu moreOptionsContextMenu = membersGridComponent.getActionGridBar().getBtnMoreOptionsContextMenu();
		moreOptionsContextMenu.addItem("Edit Members", (selectedItem) -> handleEditMembers());
		moreOptionsContextMenu.addItem("Remove Members", (selectedItem) -> handleRemoveMembers());
		moreOptionsContextMenu.addItem("Invite Someone to the Project", (selectedItem) -> handleProjectInvitationRequest());

		teamContent.addComponent(membersGridComponent);
		return teamContent;
	}

	private void handleRemoveMembers() {
		Set<Member> membersToRemove = teamGrid.getSelectedItems();

		if (membersToRemove.isEmpty()) {
			Notification.show("Info", "Please select one or more members first!", Notification.Type.HUMANIZED_MESSAGE);
			return;
		}

		// remove any owner members from the selection and display an informational message
		// TODO: allow the original owner (whose namespace the project is in) to remove any other owner
		if (membersToRemove.stream().anyMatch(member -> member.getRole() == RBACRole.OWNER)) {
			membersToRemove = membersToRemove.stream().filter(
					member -> member.getRole() != RBACRole.OWNER
			).collect(Collectors.toSet());

			Notification ownerMembersSelectedNotification = new Notification(
					"Your selection includes members with the 'Owner' role, who you cannot remove.\n"
							+ "Those members have been ignored. (click to dismiss)",
					Notification.Type.WARNING_MESSAGE
			);
			ownerMembersSelectedNotification.setDelayMsec(-1);
			ownerMembersSelectedNotification.show(Page.getCurrent());
		}

		// remove the current user from the selection and display an informational message
		Optional<Member> selectedMemberCurrentUser = membersToRemove.stream().filter(
				member -> member.getUserId().equals(project.getCurrentUser().getUserId())
		).findAny();

		if (selectedMemberCurrentUser.isPresent()) {
			membersToRemove = membersToRemove.stream().filter(
					member -> member != selectedMemberCurrentUser.get()
			).collect(Collectors.toSet());

			Notification selfSelectedNotification = new Notification(
					"You cannot remove yourself from the project.\n"
							+ "Please use the 'Leave Project' button on the project card on the dashboard instead.\n"
							+ "\n"
							+ "If you are the owner of the project, please contact support to request a transfer\n"
							+ "of ownership. (click to dismiss)",
					Notification.Type.WARNING_MESSAGE
			);
			selfSelectedNotification.setDelayMsec(-1);
			selfSelectedNotification.show(Page.getCurrent());
		}

		if (!membersToRemove.isEmpty()) {
			new RemoveMemberDialog(
					project::removeSubject,
					membersToRemove,
					evt -> eventBus.post(new MembersChangedEvent())
			).show();
		}
	}

	private void handleEditMembers() {
		Set<Member> membersToEdit = teamGrid.getSelectedItems();

		if (membersToEdit.isEmpty()) {
			Notification.show("Info", "Please select one or more members first!", Notification.Type.HUMANIZED_MESSAGE);
			return;
		}

		// remove any owner members from the selection and display an informational message
		// TODO: allow the original owner (whose namespace the project is in) to edit any other owner's role, as well as
		//       assign additional owners (also see GitlabManagerCommon.assignOnProject which does its own check)
		if (membersToEdit.stream().anyMatch(member -> member.getRole() == RBACRole.OWNER)) {
			membersToEdit = membersToEdit.stream().filter(
					member -> member.getRole() != RBACRole.OWNER
			).collect(Collectors.toSet());

			Notification ownerMembersSelectedNotification = new Notification(
					"Your selection includes members with the 'Owner' role, whose role you cannot change.\n"
							+ "Those members have been ignored. (click to dismiss)",
					Notification.Type.WARNING_MESSAGE
			);
			ownerMembersSelectedNotification.setDelayMsec(-1);
			ownerMembersSelectedNotification.show(Page.getCurrent());
		}

		if (!membersToEdit.isEmpty()) {
			new EditMemberDialog(
					project::assignRoleToSubject,
					membersToEdit,
					(evt) -> eventBus.post(new MembersChangedEvent())
			).show();
		}
	}

	public void reloadProject(ProjectReference projectReference) {
		if (project == null) {
			setProjectReference(projectReference);
			return;
		}

		setEnabled(false);
		setProgressBarVisible(true);

		final UI ui = UI.getCurrent();

		project.open(new OpenProjectListener() {
			@Override
			public void progress(String msg, Object... params) {
				ui.access(() -> {
					if (params != null) {
						progressBar.setCaption(String.format(msg, params));
					}
					else {
						progressBar.setCaption(msg);
					}
					ui.push();
				});
			}

			@Override
			public void ready(Project project) {
				ProjectView.this.project = project;

				ProjectView.this.project.addEventListener(
						ProjectEvent.exceptionOccurred,
						projectExceptionListener
				);

				ProjectView.this.project.getTagManager().addPropertyChangeListener(
						TagManagerEvent.tagsetDefinitionChanged,
						tagsetChangeListener
				);

				setProgressBarVisible(false);
				reloadAll();
				setEnabled(true);
			}

			@Override
			public void failure(Throwable t) {
				setProgressBarVisible(false);
				setEnabled(true);
				errorHandler.showAndLogError("Failed to reload project", t);
			}
		});
	}

	private void initProject(ProjectReference projectReference) {
		setEnabled(false);
		setProgressBarVisible(true);

		final UI ui = UI.getCurrent();

		projectsManager.openProject(projectReference, tagManager, new OpenProjectListener() {
			@Override
			public void progress(String msg, Object... params) {
				ui.access(() -> {
					if (params != null) {
						progressBar.setCaption(String.format(msg, params));
					}
					else {
						progressBar.setCaption(msg);
					}
					ui.push();
				});
			}

			@Override
			public void ready(Project project) {
				ProjectView.this.project = project;

				ProjectView.this.project.addEventListener(
						ProjectEvent.exceptionOccurred,
						projectExceptionListener
				);

				ProjectView.this.project.getTagManager().addPropertyChangeListener(
						TagManagerEvent.tagsetDefinitionChanged,
						tagsetChangeListener
				);

				setProgressBarVisible(false);
				reloadAll();
				setEnabled(true);
			}

			@Override
			public void failure(Throwable t) {
				setProgressBarVisible(false);
				setEnabled(true);
				errorHandler.showAndLogError("Failed to open project", t);
			}
		});
	}

	private void initData() {
		try {
			Set<Member> projectMembers = project.getProjectMembers();

			membersByIdentifier = projectMembers.stream()
					.collect(Collectors.toMap(Member::getIdentifier, Function.identity()));

			TreeDataProvider<Resource> resourceDataProvider = buildResourceDataProvider();
			documentGrid.setDataProvider(resourceDataProvider);
			documentGrid.sort(DocumentGridColumn.NAME.name());
			documentGrid.expand(resourceDataProvider.getTreeData().getRootItems());

			tagsetData = new ListDataProvider<>(project.getTagsets());
			tagsetGrid.setDataProvider(tagsetData);
			tagsetGrid.sort(TagsetGridColumn.NAME.name());

			ListDataProvider<Member> memberData = new ListDataProvider<>(projectMembers);
			teamGrid.setDataProvider(memberData);
		}
		catch (Exception e) {
			errorHandler.showAndLogError("Failed to initialize data", e);
		}
	}

	private TreeDataProvider<Resource> buildResourceDataProvider() throws Exception {
		if (project == null) {
			return new TreeDataProvider<>(new TreeData<>());
		}

		Collection<SourceDocumentReference> sourceDocumentRefs = project.getSourceDocumentReferences();

		TreeData<Resource> treeData = new TreeData<>();

		for (SourceDocumentReference sourceDocumentRef : sourceDocumentRefs) {
			DocumentResource documentResource = new DocumentResource(
						sourceDocumentRef,
						project.getId(),
						sourceDocumentRef.getResponsibleUser() != null ?
								membersByIdentifier.get(sourceDocumentRef.getResponsibleUser()) : null
			);
			treeData.addItem(null, documentResource);

			List<AnnotationCollectionReference> annotationCollectionRefs = sourceDocumentRef.getUserMarkupCollectionRefs();

			List<Resource> collectionResources = annotationCollectionRefs.stream()
					.filter(annotationCollectionRef ->
									!miToggleResponsibilityFilter.isChecked() ||
											annotationCollectionRef.isResponsible(project.getCurrentUser().getIdentifier())
					)
					.map(annotationCollectionRef -> new CollectionResource(
							annotationCollectionRef,
							project.getId(),
							annotationCollectionRef.getResponsibleUser() != null ?
									membersByIdentifier.get(annotationCollectionRef.getResponsibleUser()) : null
					))
					.collect(Collectors.toList());

			if (!collectionResources.isEmpty()) {
				treeData.addItems(
					documentResource,
					collectionResources
				);
			}
		}

		// do a locale-specific sort, assuming that all documents share the same locale
		Optional<SourceDocumentReference> optionalFirstDocument = sourceDocumentRefs.stream().findFirst();
		Locale locale = optionalFirstDocument.isPresent() ?
				optionalFirstDocument.get().getSourceDocumentInfo().getIndexInfoSet().getLocale() : Locale.getDefault();

		Collator collator = Collator.getInstance(locale);
		collator.setStrength(Collator.PRIMARY);

		documentGrid.getColumn(DocumentGridColumn.NAME.name()).setComparator(
				(r1, r2) -> collator.compare(r1.getName(), r2.getName())
		);
		tagsetGrid.getColumn(TagsetGridColumn.NAME.name()).setComparator(
				(t1, t2) -> collator.compare(t1.getName(), t2.getName())
		);

		return new TreeDataProvider<>(treeData);
	}

	@Override
	public void reloadAll() {
		boolean isMembersEditAllowed = projectsManager.isAuthorizedOnProject(projectReference, RBACPermission.PROJECT_MEMBERS_EDIT);
		teamLayout.setVisible(isMembersEditAllowed);

		initData();

		setControlsStateBasedOnProjectReadOnlyState();

		eventBus.post(new ProjectReadyEvent(project));
	}

	public void setProjectReference(ProjectReference projectReference) {
		this.projectReference = projectReference;
		eventBus.post(new HeaderContextChangeEvent(projectReference.getName()));
		initProject(projectReference);
	}

	@Subscribe
	public void handleDocumentChanged(DocumentChangeEvent documentChangeEvent) {
		// TODO: do we really need to re-init everything?
		initData();
	}

	@Subscribe
	public void handleMembersChanged(MembersChangedEvent membersChangedEvent) {
		try {
			ListDataProvider<Member> memberListDataProvider = new ListDataProvider<>(project.getProjectMembers());
			teamGrid.setDataProvider(memberListDataProvider);
		}
		catch (IOException e) {
			errorHandler.showAndLogError("Failed to load project members", e);
		}
	}

	private void handleDeleteResources(TreeGrid<Resource> resourceGrid) {
		final Set<Resource> selectedResources = resourceGrid.getSelectedItems();

		if (selectedResources.isEmpty()) {
			Notification.show("Info", "Please select one or more resources first!", Notification.Type.HUMANIZED_MESSAGE);
			return;
		}

		boolean isBeyondCurrentUsersResponsibility = selectedResources.stream().anyMatch(
				resource -> !resource.isResponsible(project.getCurrentUser().getIdentifier())
		);

		List<String> selectedResourceNames = selectedResources.stream()
				.map(Resource::getName)
				.sorted()
				.collect(Collectors.toList());
		String quotedCommaSeparatedResourceNames = String.format("\"%s\"", String.join("\", \"", selectedResourceNames));

		try {
			BeyondResponsibilityConfirmDialog.executeWithConfirmDialog(
					isBeyondCurrentUsersResponsibility,
					project.hasPermission(project.getCurrentUserProjectRole(), RBACPermission.DOCUMENT_DELETE_OR_EDIT),
					new Action() {
						@Override
						public void execute() {
							ConfirmDialog.show(
									UI.getCurrent(),
									"Warning",
									String.format(
											"Are you sure you want to delete resource(s) %s?",
											quotedCommaSeparatedResourceNames
									),
									"Delete",
									"Cancel",
									dlg -> {
										if (dlg.isConfirmed()) {
											try {
												// this sort ensures that we delete collections before we delete documents
												List<Resource> sortedSelectedResources = selectedResources.stream()
														.sorted(new Comparator<Resource>() {
															@Override
															public int compare(Resource r1, Resource r2) {
																if (r1.isCollection() && !r2.isCollection()) {
																	return -1;
																}
																else if (!r1.isCollection() && r2.isCollection()) {
																	return 1;
																}
																else {
																	return r1.getResourceId().compareTo(r2.getResourceId());
																}
															}
														})
														.collect(Collectors.toList());

												for (Resource resource : sortedSelectedResources) {
													resource.deleteFrom(project);
												}
											}
											catch (Exception e) {
												errorHandler.showAndLogError(
														String.format("Failed to delete resource(s) %s", quotedCommaSeparatedResourceNames),
														e
												);
											}
										}
									}
							);
						}
					}
			);
		}
		catch (IOException e) {
			errorHandler.showAndLogError(
					String.format("Failed to delete resource(s) %s", quotedCommaSeparatedResourceNames),
					e
			);
		}
	}

	private void handleAnalyzeResources(TreeGrid<Resource> resourceGrid) {
		final Set<Resource> selectedResources = resourceGrid.getSelectedItems();

		if (selectedResources.isEmpty()) {
			Notification.show("Info", "Please select one or more resources first!", Notification.Type.HUMANIZED_MESSAGE);
			return;
		}

		try {
			Corpus corpusToBeAnalyzed = new Corpus();

			for (Resource resource : selectedResources) {
				if (resource.isCollection()) {
					CollectionResource collectionResource = (CollectionResource) resource;
					corpusToBeAnalyzed.addUserMarkupCollectionReference(collectionResource.getCollectionReference());

					DocumentResource parentDocumentResource = (DocumentResource) resourceGrid.getTreeData().getParent(collectionResource);
					if (!corpusToBeAnalyzed.getSourceDocuments().contains(parentDocumentResource.getDocument())) {
						corpusToBeAnalyzed.addSourceDocument(parentDocumentResource.getDocument());
					}
				}
				else { // it's a document
					DocumentResource documentResource = (DocumentResource) resource;
					corpusToBeAnalyzed.addSourceDocument(documentResource.getDocument());
				}
			}

			eventBus.post(new RouteToAnalyzeEvent((IndexedProject) project, corpusToBeAnalyzed));
		}
		catch (Exception e) {
			errorHandler.showAndLogError("Failed to create corpus for analysis", e);
		}
	}

	public void close() {
		try {
			eventBus.unregister(this);

			if (projectResourceExportApiDialog != null) {
				projectResourceExportApiDialog.removeRequestHandlerFromVaadinService();
			}

			if (project != null) {
				if (projectExceptionListener != null) {
					project.removeEventListener(
							ProjectEvent.exceptionOccurred,
							projectExceptionListener
					);
				}

				if (tagsetChangeListener != null) {
					project.getTagManager().removePropertyChangeListener(
							TagManagerEvent.tagsetDefinitionChanged,
							tagsetChangeListener
					);
				}

				project.close();
				project = null;
			}
		}
		catch (Exception e) {
			errorHandler.showAndLogError("Failed to close ProjectView", e);
		}
	}

	private SerializablePredicate<Object> createDocumentGridComponentSearchFilterProvider(String searchInput) {
		@SuppressWarnings("unchecked")
		TreeDataProvider<Resource> resourceDataProvider = (TreeDataProvider<Resource>) documentGrid.getDataProvider();
		TreeData<Resource> resourceData = resourceDataProvider.getTreeData();

		return new SerializablePredicate<Object>() {
			@Override
			public boolean test(Object obj) {
				boolean isMatch = obj.toString().toLowerCase().contains(searchInput.toLowerCase());

				if (obj instanceof CollectionResource) {
					return isMatch;
				}
				else { // it's a document
					if (isMatch) {
						return true;
					}
					else { // check child collections
						return resourceData.getChildren((Resource) obj).stream().anyMatch(
								child -> child.toString().toLowerCase().contains(searchInput.toLowerCase())
						);
					}
				}
			}
		};
	}

	private SerializablePredicate<Object> createTagsetGridComponentSearchFilterProvider(String searchInput) {
		return new SerializablePredicate<Object>() {
			@Override
			public boolean test(Object obj) {
				if (obj instanceof TagsetDefinition) {
					String name = ((TagsetDefinition) obj).getName();
					return name != null && name.toLowerCase().contains(searchInput.toLowerCase());
				}
				return false;
			}
		};
	}

	private void handleShareProjectResources() {
		if (projectResourceExportApiDialog == null) {
			projectResourceExportApiDialog = new ProjectResourceExportApiDialog(project);
		}
		projectResourceExportApiDialog.show();
	}
 }
