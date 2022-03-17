package de.catma.ui.module.main;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

import org.vaadin.dialogs.ConfirmDialog;
import org.vaadin.dialogs.ConfirmDialog.ContentMode;

import com.google.common.eventbus.EventBus;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import de.catma.project.ProjectsManager;
import de.catma.properties.CATMAPropertyKey;
import de.catma.repository.git.interfaces.IRemoteGitManagerRestricted;
import de.catma.ui.CatmaRouter;
import de.catma.ui.events.HeaderContextChangeEvent;
import de.catma.ui.events.QueryResultRowInAnnotateEvent;
import de.catma.ui.events.routing.RouteToAnalyzeEvent;
import de.catma.ui.events.routing.RouteToAnnotateEvent;
import de.catma.ui.events.routing.RouteToConflictedProjectEvent;
import de.catma.ui.events.routing.RouteToDashboardEvent;
import de.catma.ui.events.routing.RouteToProjectEvent;
import de.catma.ui.events.routing.RouteToTagsEvent;
import de.catma.ui.login.LoginService;
import de.catma.ui.module.analyze.AnalyzeManagerView;
import de.catma.ui.module.annotate.TaggerManagerView;
import de.catma.ui.module.dashboard.DashboardView;
import de.catma.ui.module.project.ConflictedProjectView;
import de.catma.ui.module.project.ProjectView;
import de.catma.ui.module.tags.TagsView;

/**
 * Main entrypoint for CATMA, it renders a header and a main section
 *
 * @author db
 */
public class MainView extends VerticalLayout implements CatmaRouter, Closeable {

	private final CatmaHeader header;

	/*
	 * mainSection is the combined section (navigation and viewSection / content) of CATMA
	 */
	private final HorizontalLayout mainSection = new HorizontalLayout();

	/*
	 * left side main navigation
	 */
	private final CatmaNav navigation;

	/*
	 * viewSection is the content section
	 */
	private final VerticalLayout viewSection = new VerticalLayout();

	/*
	 * global communication via EventBus
	 */
	private final EventBus eventBus;

	private final ProjectsManager projectManager;

	private final IRemoteGitManagerRestricted remoteGitManagerRestricted;

	private Class<?> currentRoute;
	private ProjectView projectView;
	private TagsView tagsView;
	private TaggerManagerView taggerManagerView;
	private AnalyzeManagerView analyzeManagerView;

	public MainView(
			ProjectsManager projectManager,
			CatmaHeader catmaHeader,
			EventBus eventBus,
			IRemoteGitManagerRestricted remoteGitManagerRestricted,
			LoginService loginLogoutService,
			boolean termsOfUseConsentGiven,
			Consumer<Boolean> termsOfUseUpdater
	) {
		this.projectManager = projectManager;
		this.header = catmaHeader;
		this.eventBus = eventBus;
		this.remoteGitManagerRestricted = remoteGitManagerRestricted;
		this.navigation = new CatmaNav(eventBus);

		initComponents();

		eventBus.register(this);

		if (!termsOfUseConsentGiven) {
			ConfirmDialog dlg = ConfirmDialog.show(
					UI.getCurrent(),
					"Terms of Use & Privacy Policy",
					String.format(
							"Please read our " +
									"<a href=\"%1$s\" target=\"_blank\">Terms of Use</a> " +
									"and our " +
									"<a href=\"%2$s\" target=\"_blank\">Privacy Policy</a> " +
									"carefully. You need to accept both in order to continue to work with CATMA.<br />" +
									"<br />" +
									"Do you <strong>accept</strong> our Terms of Use and our Privacy Policy?",
							CATMAPropertyKey.TermsOfUseURL.getValue(CATMAPropertyKey.TermsOfUseURL.getDefaultValue()),
							CATMAPropertyKey.PrivacyPolicyURL.getValue(CATMAPropertyKey.PrivacyPolicyURL.getDefaultValue())
					),
					"Yes",
					"No",
					new ConfirmDialog.Listener() {
						@Override
						public void onClose(ConfirmDialog dialog) {
							if (dialog.isConfirmed()) {
								termsOfUseUpdater.accept(true);
							}
							else {
								loginLogoutService.logout();
							}
						}
					}
			);

			dlg.setContentMode(ContentMode.HTML);
		}
	}

	private void initComponents() {
		setSizeFull();
		setSpacing(false);
		addStyleName("main-view");

		addComponent(header);
		addComponent(mainSection);

		setExpandRatio(mainSection, 1f);

		viewSection.setSizeFull();
		viewSection.addStyleName("view-section");

		mainSection.setSizeFull();
		mainSection.setSpacing(false);
		mainSection.addStyleName("main-section");

		mainSection.addComponent(navigation);
		mainSection.addComponent(viewSection);

		mainSection.setExpandRatio(viewSection, 1f);
	}

	private void setContent(Component component) {
		viewSection.removeStyleName("no-margin-view-section");
		viewSection.removeAllComponents();
		viewSection.addComponent(component);
	}

	private void closeViews() {
		if (projectView != null) {
			projectView.close();
			projectView = null;
		}
		if (tagsView != null) {
			tagsView.close();
			tagsView = null;
		}
		if (taggerManagerView != null) {
			taggerManagerView.closeClosables();
			taggerManagerView = null;
		}
		if (analyzeManagerView != null) {
			analyzeManagerView.closeClosables();
			analyzeManagerView = null;
		}
	}

	@Override
	public void handleRouteToDashboard(RouteToDashboardEvent routeToDashboardEvent) {
		closeViews();
		if (isNewTarget(routeToDashboardEvent.getClass())) {
			setContent(new DashboardView(projectManager, remoteGitManagerRestricted, eventBus));
			viewSection.addStyleName("no-margin-view-section");
			eventBus.post(new HeaderContextChangeEvent());
			currentRoute = routeToDashboardEvent.getClass();
		}
	}

	@Override
	public void handleRouteToProject(RouteToProjectEvent routeToProjectEvent) {
		if (isNewTarget(routeToProjectEvent.getClass())) {
			if (projectView == null) {
				projectView = new ProjectView(projectManager, eventBus);
				setContent(projectView);
				projectView.setProjectReference(routeToProjectEvent.getProjectReference());
			}
			else {
				setContent(projectView);
				if (routeToProjectEvent.isReloadProject()) {
					projectView.reloadProject(routeToProjectEvent.getProjectReference());
				}
			}
			currentRoute = routeToProjectEvent.getClass();
		}
	}

	@Override
	@Deprecated
	public void handleRouteToConflictedProject(RouteToConflictedProjectEvent routeToConflictedProjectEvent) {
		if (isNewTarget(routeToConflictedProjectEvent.getClass())) {
			ConflictedProjectView conflictedProjectView = new ConflictedProjectView(routeToConflictedProjectEvent.getConflictedProject(), eventBus);
			setContent(conflictedProjectView);
			currentRoute = routeToConflictedProjectEvent.getClass();
		}
	}

	@Override
	public void handleRouteToAnnotate(RouteToAnnotateEvent routeToAnnotateEvent) {
		if (isNewTarget(routeToAnnotateEvent.getClass())) {
			if (taggerManagerView == null) {
				taggerManagerView = new TaggerManagerView(eventBus, routeToAnnotateEvent.getProject());
			}

			setContent(taggerManagerView);

			if (routeToAnnotateEvent.getDocument() != null) {
				taggerManagerView.openSourceDocument(routeToAnnotateEvent.getDocument(), routeToAnnotateEvent.getProject(), null);
			}

			currentRoute = routeToAnnotateEvent.getClass();
		}
	};

	@Override
	public void handleRouteToAnnotate(QueryResultRowInAnnotateEvent queryResultRowInAnnotateEvent) {
		if (isNewTarget(queryResultRowInAnnotateEvent.getClass())) {
			if (taggerManagerView == null) {
				taggerManagerView = new TaggerManagerView(eventBus, queryResultRowInAnnotateEvent.getProject());
			}

			setContent(taggerManagerView);

			try {
				taggerManagerView.openSourceDocument(
					queryResultRowInAnnotateEvent.getProject().getSourceDocument(queryResultRowInAnnotateEvent.getDocumentId()),
					queryResultRowInAnnotateEvent.getProject(),
					(taggerView) -> {
						taggerView.showQueryResultRows(queryResultRowInAnnotateEvent.getSelection(), queryResultRowInAnnotateEvent.getRows());
					}
				);
			}
			catch (Exception e) {
				((ErrorHandler)UI.getCurrent()).showAndLogError("Error opening document in annotate module!", e);
			}

			currentRoute = queryResultRowInAnnotateEvent.getClass();
		}
	};

	@Override
	public void handleRouteToTags(RouteToTagsEvent routeToTagsEvent) {
		if (isNewTarget(routeToTagsEvent.getClass())) {
			if (tagsView == null) {
				tagsView = new TagsView(eventBus, routeToTagsEvent.getProject());
			}

			setContent(tagsView);

			if (routeToTagsEvent.getTagset() != null) {
				tagsView.setSelectedTagset(routeToTagsEvent.getTagset());
			}

			currentRoute = routeToTagsEvent.getClass();
		}
	}

	@Override
	public void handleRouteToAnalyze(RouteToAnalyzeEvent routeToAnalyzeEvent) {
		if (isNewTarget(routeToAnalyzeEvent.getClass())) {
			if (analyzeManagerView == null) {
				analyzeManagerView = new AnalyzeManagerView(eventBus, routeToAnalyzeEvent.getProject());
			}

			if (routeToAnalyzeEvent.getCorpus() != null) {
				analyzeManagerView.analyzeNewDocuments(routeToAnalyzeEvent.getCorpus(), routeToAnalyzeEvent.getProject());
			}

			setContent(analyzeManagerView);

			currentRoute = routeToAnalyzeEvent.getClass();
		}
	}

	@Override
	public Class<?> getCurrentRoute() {
		return currentRoute;
	}

	@Override
	public void close() throws IOException {
		closeViews();
	}
}
