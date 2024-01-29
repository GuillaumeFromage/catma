package de.catma.repository.git.managers;

import com.google.common.collect.Lists;
import de.catma.project.CommitInfo;
import de.catma.properties.CATMAPropertyKey;
import de.catma.repository.git.managers.interfaces.LocalGitRepositoryManager;
import de.catma.repository.git.managers.jgit.ClosableRecursiveMerger;
import de.catma.repository.git.managers.jgit.JGitCommandFactory;
import de.catma.repository.git.managers.jgit.RelativeJGitCommandFactory;
import de.catma.user.User;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class JGitRepoManager implements LocalGitRepositoryManager, AutoCloseable {
	private final Logger logger = Logger.getLogger(JGitRepoManager.class.getName());

	private final String repositoryBasePath;
	private final String username;
	private final JGitCommandFactory jGitCommandFactory;

	private Git gitApi;

	/**
	 * Creates a new instance of this class for the given {@link User}.
	 * <p>
	 * Note that the <code>user</code> argument is NOT used for authentication to remote Git servers. It is only
	 * used to organise repositories on the local file system, based on the user's identifier. Methods of this class
	 * that require authentication expect a {@link JGitCredentialsManager}.
	 *
	 * @param repositoryBasePath the base path for local repository storage
	 * @param user a {@link User}
	 */
	public JGitRepoManager(String repositoryBasePath, User user) {
		this.repositoryBasePath = repositoryBasePath;
		this.username = user.getIdentifier();
		this.jGitCommandFactory = new RelativeJGitCommandFactory();
	}

	// methods that can always be called, irrespective of the instance state
	public String getUsername() {
		return username;
	}

	public Git getGitApi() {
		return gitApi;
	}

	@Override
	public File getUserRepositoryBasePath() {
		return Paths.get(new File(repositoryBasePath).toURI()).resolve(username).toFile();
	}

	@Override
	public boolean isAttached() {
		return gitApi != null;
	}

	@Override
	public void detach() {
		close();
	}


	// methods that require the instance to be in a detached state
	@Override
	public String clone(String namespace, String name, String uri, JGitCredentialsManager jGitCredentialsManager) throws IOException {
		return clone(namespace, name, uri, jGitCredentialsManager, 0, 0);
	}

	private String clone(
			String namespace,
			String name,
			String uri,
			JGitCredentialsManager jGitCredentialsManager,
			int refreshCredentialsTryCount,
			int tryCount
	) throws IOException {
		if (isAttached()) {
			throw new IllegalStateException("Can't call `clone` on an attached instance");
		}

		File targetPath = Paths.get(getUserRepositoryBasePath().toURI())
				.resolve(namespace)
				.resolve(name)
				.toFile();

		try {
			CloneCommand cloneCommand = jGitCommandFactory.newCloneCommand().setURI(uri).setDirectory(targetPath);
			cloneCommand.setCredentialsProvider(jGitCredentialsManager.getCredentialsProvider());
			gitApi = cloneCommand.call();
		}
		catch (GitAPIException e) {
			if (e instanceof TransportException && e.getMessage().contains("not authorized") && refreshCredentialsTryCount < 1) {
				// it's likely that the user is logged in using the username/password authentication method and that their
				// GitLab OAuth access token has expired - try to refresh credentials and retry the operation once
				jGitCredentialsManager.refreshTransientCredentials();
				return clone(namespace, name, uri, jGitCredentialsManager, refreshCredentialsTryCount + 1, tryCount);
			}

			if (e instanceof TransportException && e.getMessage().contains("authentication not supported") && tryCount < 3) {
				// sometimes GitLab refuses to accept the clone and returns this error message
				// subsequent clone attempts succeed however, so we retry the clone up to 3 times before giving up
				try {
					Thread.sleep(100L * (tryCount + 1));
				}
				catch (InterruptedException ignored) {}

				return clone(namespace, name, uri, jGitCredentialsManager, refreshCredentialsTryCount, tryCount + 1);
			}

			// give up, refreshing credentials didn't work, retries exhausted, or unexpected error
			throw new IOException(
					String.format("Failed to clone, tried %d times", tryCount + 1),
					e
			);
		}

		return targetPath.getName();
	}

	@Override
	public void open(String namespace, String name) throws IOException {
		if (isAttached()) {
			throw new IllegalStateException("Can't call `open` on an attached instance");
		}

		File repositoryPath = Paths.get(getUserRepositoryBasePath().toURI())
				.resolve(namespace)
				.resolve(name)
				.toFile();

		File dotGitDirOrFile = new File(repositoryPath, ".git");

		if (!repositoryPath.exists() || !repositoryPath.isDirectory() || !dotGitDirOrFile.exists()) {
			throw new IOException(
					String.format("Couldn't find a Git repository at path %s.", repositoryPath)
			);
		}

		// handle opening of submodule repos
		if (dotGitDirOrFile.isFile()) {
			dotGitDirOrFile = Paths.get(repositoryPath.toURI()).resolve(
					FileUtils.readFileToString(dotGitDirOrFile, StandardCharsets.UTF_8)
							.replace("gitdir:", "")
							.trim()
			).normalize().toFile();
		}

		gitApi = Git.open(dotGitDirOrFile);
	}


	// methods that require the instance to be in an attached state
	@Override
	public String getRemoteUrl(String remoteName) {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getRemoteUrl` on a detached instance");
		}

		if (remoteName == null) {
			remoteName = "origin";
		}

		StoredConfig config = gitApi.getRepository().getConfig();
		return config.getString("remote", remoteName, "url");
	}

	@Override
	public List<String> getRemoteBranches() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getRemoteBranches` on a detached instance");
		}

		try {
			List<Ref> branches = gitApi.branchList().setListMode(ListMode.REMOTE).call();
			return branches.stream().map(Ref::getName).collect(Collectors.toList());
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to get remote branches", e);
		}
	}


	@Override
	public String getRevisionHash() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getRevisionHash` on a detached instance");
		}

		ObjectId headRevision = gitApi.getRepository().resolve(Constants.HEAD);

		return headRevision == null ? NO_COMMITS_YET : headRevision.getName();
	}

	@Override
	public void fetch(JGitCredentialsManager jGitCredentialsManager) throws IOException {
		fetch(jGitCredentialsManager, 0, 0);
	}

	private void fetch(JGitCredentialsManager jGitCredentialsManager, int refreshCredentialsTryCount, int tryCount) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `fetch` on a detached instance");
		}

		try {
			FetchCommand fetchCommand = gitApi.fetch();
			fetchCommand.setCredentialsProvider(jGitCredentialsManager.getCredentialsProvider());
			fetchCommand.call();
		}
		catch (GitAPIException e) {
			if (e instanceof TransportException && e.getMessage().contains("not authorized") && refreshCredentialsTryCount < 1) {
				// it's likely that the user is logged in using the username/password authentication method and that their
				// GitLab OAuth access token has expired - try to refresh credentials and retry the operation once
				jGitCredentialsManager.refreshTransientCredentials();
				fetch(jGitCredentialsManager, refreshCredentialsTryCount + 1, tryCount);
				return;
			}

			if (e instanceof TransportException && e.getMessage().contains("authentication not supported") && tryCount < 3) {
				// sometimes GitLab refuses to accept the fetch and returns this error message
				// subsequent fetch attempts succeed however, so we retry the fetch up to 3 times before giving up
				try {
					Thread.sleep(100L * (tryCount + 1));
				}
				catch (InterruptedException ignored) {}

				fetch(jGitCredentialsManager, refreshCredentialsTryCount, tryCount + 1);
				return;
			}

			// give up, refreshing credentials didn't work, retries exhausted, or unexpected error
			throw new IOException(
					String.format("Failed to fetch, tried %d times", tryCount + 1),
					e
			);
		}
	}

	@Override
	public Set<String> verifyDeletedResourcesViaLog(String resourceDir, String resourceTypeKeywords, Set<String> resourceIds) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `verifyDeletedResourcesViaLog` on a detached instance");
		}

		try {
			Set<String> result = new HashSet<>();
			ObjectId userBranch = gitApi.getRepository().resolve("refs/heads/" + username);

			if (userBranch == null) {
				return result;
			}

			for (RevCommit revCommit : gitApi.log().add(userBranch).addPath(resourceDir).call()) {
				String fullCommitMessageLowerCase = revCommit.getFullMessage().toLowerCase();

				// NB: comparison with commit messages generated by:
				// - GitSourceDocumentHandler.removeDocument
				// - GitAnnotationCollectionHandler.removeCollection
				// - GitTagsetHandler.removeTagsetDefinition
				if (fullCommitMessageLowerCase.startsWith(String.format("deleted %s", resourceTypeKeywords.toLowerCase()))) {
					for (String resourceId : resourceIds) {
						if (fullCommitMessageLowerCase.contains(resourceId.toLowerCase())) {
							result.add(resourceId);
						}
					}
				}
			}

			return result;
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to verify deleted resources", e);
		}
	}

	@Override
	public void checkout(String name) throws IOException {
		checkout(name, false);
	}

	@Override
	public void checkout(String name, boolean createBranch) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `checkout` on a detached instance");
		}

		try {
			if (gitApi.getRepository().getBranch().equals(name)) {
				// already the active branch
				return;
			}

			CheckoutCommand checkoutCommand = gitApi.checkout();

			if (createBranch) {
				// check if the branch already exists
				List<Ref> refs = gitApi.branchList().call();
				boolean branchAlreadyExists = refs.stream()
						.map(Ref::getName)
						.anyMatch(refName -> refName.equals("refs/heads/" + name));

				if (branchAlreadyExists) {
					createBranch = false;
				}
				else {
					// set the start point for the checkout to master, if we can
					try (RevWalk revWalk = new RevWalk(gitApi.getRepository())) {
						ObjectId masterBranch = gitApi.getRepository().resolve("refs/heads/" + Constants.MASTER);
						// can be null if a project is still empty - in that case branch creation will fail,
						// because JGit cannot create a branch without a start point
						if (masterBranch != null) {
							RevCommit masterHeadCommit = revWalk.parseCommit(masterBranch);
							checkoutCommand.setStartPoint(masterHeadCommit);
						}
					}
				}
			}

			checkoutCommand.setCreateBranch(createBranch);
			checkoutCommand.setName(name).call();

			if (createBranch) {
				// update config to set 'remote' and 'merge' (upstream branch) for this branch
				StoredConfig config = gitApi.getRepository().getConfig();
				config.setString(
						ConfigConstants.CONFIG_BRANCH_SECTION,
						name,
						ConfigConstants.CONFIG_KEY_REMOTE,
						"origin"
				);
				config.setString(
						ConfigConstants.CONFIG_BRANCH_SECTION,
						name,
						ConfigConstants.CONFIG_KEY_MERGE,
						"refs/heads/" + name
				);
				config.save();
			}
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to checkout", e);
		}
	}

	@Override
	public Status getStatus() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getStatus` on a detached instance");
		}

		try {
			return gitApi.status().call();
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to get status", e);
		}
	}

	@Override
	public boolean hasUntrackedChanges() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `hasUntrackedChanges` on a detached instance");
		}

		try {
			Status status = gitApi.status().call();
			return !status.getUntracked().isEmpty();
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to check for untracked changes", e);
		}
	}

	@Override
	public boolean hasUncommittedChanges() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `hasUncommittedChanges` on a detached instance");
		}

		try {
			return gitApi.status().call().hasUncommittedChanges();
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to check for uncommitted changes", e);
		}
	}

	@Override
	public void add(File relativeTargetFile) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `add` on a detached instance");
		}

		try {
			gitApi.add()
					.addFilepattern(FilenameUtils.separatorsToUnix(relativeTargetFile.toString()))
					.call();
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to add", e);
		}
	}

	@Override
	public void add(File targetFile, byte[] bytes) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `add` on a detached instance");
		}

		try (FileOutputStream fileOutputStream = FileUtils.openOutputStream(targetFile)) {
			fileOutputStream.write(bytes);

			Path basePath = gitApi.getRepository().getWorkTree().toPath();
			Path absoluteFilePath = Paths.get(targetFile.getAbsolutePath());
			Path relativeFilePath = basePath.relativize(absoluteFilePath);

			gitApi.add()
					.addFilepattern(FilenameUtils.separatorsToUnix(relativeFilePath.toString()))
					.call();
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to add", e);
		}
	}

	@Override
	public String addAndCommit(File targetFile, byte[] bytes, String commitMsg, String committerName, String committerEmail) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `addAndCommit` on a detached instance");
		}

		try {
			add(targetFile, bytes);
			return commit(commitMsg, committerName, committerEmail, false);
		}
		catch (IOException e) {
			throw new IOException("Failed to add and commit", e);
		}
	}

	// stages all new, modified and deleted files
	// this is different to commit with all=true, which only stages modified and deleted files
	@Override
	public String addAllAndCommit(String message, String committerName, String committerEmail, boolean force) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `addAllAndCommit` on a detached instance");
		}

		try {
			gitApi.add().addFilepattern(".").call();

			List<DiffEntry> diffEntries = gitApi.diff().call();
			if (!diffEntries.isEmpty()) {
				RmCommand rmCommand = gitApi.rm();
				for (DiffEntry entry : diffEntries) {
					if (entry.getChangeType().equals(ChangeType.DELETE)) {
						rmCommand.addFilepattern(entry.getOldPath());
					}
				}
				rmCommand.call();
			}

			if (force || gitApi.status().call().hasUncommittedChanges()) {
				return commit(message, committerName, committerEmail, force);
			}
			else {
				return getRevisionHash();
			}
		}
		catch (GitAPIException | IOException e) {
			throw new IOException("Failed to add all and commit", e);
		}
	}

	@Override
	public void remove(File targetFile) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `remove` on a detached instance");
		}

		try {
			Path basePath = gitApi.getRepository().getWorkTree().toPath();
			Path absoluteFilePath = Paths.get(targetFile.getAbsolutePath());
			Path relativeFilePath = basePath.relativize(absoluteFilePath);

			gitApi.rm()
					.setCached(true)
					.addFilepattern(FilenameUtils.separatorsToUnix(relativeFilePath.toString()))
					.call();

			// TODO: why don't we trust JGit to delete? (setCached(false) above, the default)
			if (targetFile.isDirectory()) {
				FileUtils.deleteDirectory(targetFile);
			}
			else if (targetFile.exists() && !targetFile.delete()) {
				throw new IOException(
						String.format("Unable to delete file %s.", targetFile) // like message from FileUtils.deleteDirectory above
				);
			}
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to remove", e);
		}
	}

	@Override
	public String removeAndCommit(
			File targetFile,
			boolean removeEmptyParent,
			String commitMsg,
			String committerName,
			String committerEmail
	) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `removeAndCommit` on a detached instance");
		}

		try {
			remove(targetFile);

			File parentDir = targetFile.getParentFile();
			if (removeEmptyParent && parentDir != null && parentDir.isDirectory()) {
				String[] content = parentDir.list();
				if (content != null && content.length == 0) {
					parentDir.delete();
				}
			}

			return commit(commitMsg, committerName, committerEmail, false);
		}
		catch (IOException e) {
			throw new IOException("Failed to remove and commit", e);
		}
	}

	@Override
	public String commit(String message, String committerName, String committerEmail, boolean force) throws IOException {
		return commit(message, committerName, committerEmail, false, force);
	}

	@Override
	public String commit(String message, String committerName, String committerEmail, boolean all, boolean force) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `commit` on a detached instance");
		}

		try {
			if (!gitApi.status().call().hasUncommittedChanges() && !force) {
				return getRevisionHash();
			}

			return gitApi.commit()
					.setMessage(message)
					.setCommitter(committerName, committerEmail)
					.setAll(all)
					.call()
					.getName();
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to commit", e);
		}
	}

	@Override
	public boolean canMerge(String branch) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `canMerge` on a detached instance");
		}

		try {
			Repository repository = gitApi.getRepository();

			try (ClosableRecursiveMerger merger = new ClosableRecursiveMerger(repository, true)) {
				Ref ref = repository.findRef(branch);

				if (ref == null) {
					return false;
				}

				ObjectId head = repository.resolve(Constants.HEAD);
				return merger.merge(true, head, ref.getObjectId());
			}
		}
		catch (IOException e) {
			throw new IOException(
					String.format("Failed to check if branch %s can be merged", branch),
					e
			);
		}
	}

	@Override
	public MergeResult merge(String branch) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `merge` on a detached instance");
		}

		try {
			MergeCommand mergeCommand = gitApi.merge();
			mergeCommand.setFastForward(FastForwardMode.FF);

			Ref ref = gitApi.getRepository().findRef(branch);

			if (ref != null) {
				mergeCommand.include(ref);
			}

			return mergeCommand.call();
		}
		catch (GitAPIException e) {
			throw new IOException(
					String.format("Failed to merge branch %s", branch),
					e
			);
		}
	}

	@Override
	public void abortMerge() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `abortMerge` on a detached instance");
		}

		try {
			Repository repository = gitApi.getRepository();

			// clear the merge state
			repository.writeMergeCommitMsg(null);
			repository.writeMergeHeads(null);

			// hard reset the index and working directory to HEAD
			gitApi.reset().setMode(ResetType.HARD).call();
		}
		catch (GitAPIException | IOException e) {
			throw new IOException("Failed to abort merge", e);
		}
	}

	@Override
	public List<PushResult> push(JGitCredentialsManager jGitCredentialsManager) throws IOException {
		return push(jGitCredentialsManager, null, false, 0, 0);
	}

	@Override
	public List<PushResult> pushMaster(JGitCredentialsManager jGitCredentialsManager) throws IOException {
		return push(jGitCredentialsManager, Constants.MASTER, false, 0, 0);
	}

	/**
	 * Pushes commits made locally on the specified branch to the associated remote ('origin') repository and branch.
	 *
	 * @param jGitCredentialsManager a {@link JGitCredentialsManager} to use for authentication
	 * @param branch the branch to push, defaults to the user branch if null
	 * @param skipBranchChecks whether to skip branch checks, normally false
	 * @param refreshCredentialsTryCount how often this push has been attempted already (start with 0, used internally to limit recursive retries)
	 * @param tryCount how often this push has been attempted already (start with 0, used internally to limit recursive retries)
	 * @return a {@link List<PushResult>} containing the results of the push operation
	 * @throws IOException if an error occurs when pushing
	 */
	private List<PushResult> push(
			JGitCredentialsManager jGitCredentialsManager,
			String branch,
			boolean skipBranchChecks,
			int refreshCredentialsTryCount,
			int tryCount
	) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `push` on a detached instance");
		}

		if (CATMAPropertyKey.DEV_PREVENT_PUSH.getBooleanValue()) {
			logger.warning(String.format("FAKE PUSH - %s", getRemoteUrl(null)));
			return new ArrayList<>();
		}

		try {
			String currentBranch = gitApi.getRepository().getBranch();

			if (!skipBranchChecks) {
				if (branch != null && !currentBranch.equals(branch)) {
					throw new IOException(
							String.format(
									"Aborting push - branch to push was \"%s\" but currently checked out branch is \"%s\"",
									branch,
									currentBranch
							)
					);
				}

				if (branch == null && !currentBranch.equals(username)) {
					throw new IOException(
							String.format(
									"Aborting push - branch to push was null (= user branch \"%s\") but currently checked out branch is \"%s\"",
									username,
									currentBranch
							)
					);
				}
			}

			PushCommand pushCommand = gitApi.push();
			pushCommand.setCredentialsProvider(jGitCredentialsManager.getCredentialsProvider());
			pushCommand.setRemote(Constants.DEFAULT_REMOTE_NAME);
			Iterable<PushResult> pushResults = pushCommand.call();

			for (PushResult pushResult : pushResults) {
				for (RemoteRefUpdate remoteRefUpdate : pushResult.getRemoteUpdates()) {
					logger.info(String.format("PushResult: %s", remoteRefUpdate));
				}
			}

			return Lists.newArrayList(pushResults);
		}
		catch (GitAPIException e) {
			if (e instanceof TransportException && e.getMessage().contains("not authorized") && refreshCredentialsTryCount < 1) {
				// it's likely that the user is logged in using the username/password authentication method and that their
				// GitLab OAuth access token has expired - try to refresh credentials and retry the operation once
				jGitCredentialsManager.refreshTransientCredentials();
				return push(jGitCredentialsManager, branch, skipBranchChecks, refreshCredentialsTryCount + 1, tryCount);
			}

			if (e instanceof TransportException && e.getMessage().contains("authentication not supported") && tryCount < 3) {
				// sometimes GitLab refuses to accept the push and returns this error message
				// subsequent push attempts succeed however, so we retry the push up to 3 times before giving up
				try {
					Thread.sleep(100L * (tryCount + 1));
				}
				catch (InterruptedException ignored) {}

				return push(jGitCredentialsManager, branch, skipBranchChecks, refreshCredentialsTryCount, tryCount + 1);
			}

			// give up, refreshing credentials didn't work, retries exhausted, or unexpected error
			throw new IOException(
					String.format("Failed to push, tried %d times", tryCount + 1),
					e
			);
		}
	}


	@Override
	public Set<String> getAdditiveBranchDifferences(String otherBranchName) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getAdditiveBranchDifferences` on a detached instance");
		}

		try {
			checkout(username, true); // the user branch has to be present at this point

			DiffCommand diffCommand = gitApi.diff();

			ObjectId thisUserBranchHeadRevisionTree = gitApi.getRepository().resolve("refs/heads/" + username + "^{tree}");
			ObjectId otherBranchRevisionTree = gitApi.getRepository().resolve(otherBranchName + "^{tree}");

			Set<String> changedPaths = new HashSet<>();

			if (thisUserBranchHeadRevisionTree != null && otherBranchRevisionTree != null) {
				ObjectReader reader = gitApi.getRepository().newObjectReader();

				CanonicalTreeParser thisUserBranchHeadRevisionTreeParser = new CanonicalTreeParser();
				thisUserBranchHeadRevisionTreeParser.reset(reader, thisUserBranchHeadRevisionTree);

				CanonicalTreeParser otherBranchRevisionTreeParser = new CanonicalTreeParser();
				otherBranchRevisionTreeParser.reset(reader, otherBranchRevisionTree);

				diffCommand.setOldTree(thisUserBranchHeadRevisionTreeParser);
				diffCommand.setNewTree(otherBranchRevisionTreeParser);

				List<DiffEntry> diffResult = diffCommand.call();

				for (DiffEntry diffEntry : diffResult) {
					if (!diffEntry.getChangeType().equals(DiffEntry.ChangeType.DELETE)) {
						changedPaths.add(diffEntry.getNewPath());
					}
				}
			}

			return changedPaths;
		}
		catch (GitAPIException | IOException e) {
			throw new IOException("Failed to get additive branch differences", e);
		}
	}


	@Override
	public List<CommitInfo> getOurUnpublishedChanges() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getOurUnpublishedChanges` on a detached instance");
		}

		try {
			List<CommitInfo> result = new ArrayList<>();

			if (gitApi.getRepository().resolve(Constants.HEAD) == null) {
				return result; // no HEAD -> new empty project, no commits yet
			}

			List<String> remoteBranches = getRemoteBranches();
			if (remoteBranches.isEmpty()) {
				return result; // project has never been synchronized
			}

			ObjectId originMaster = gitApi.getRepository().resolve("refs/remotes/origin/" + Constants.MASTER);
			if (originMaster == null) {
				return result; // can't find origin/master
			}

			Iterable<RevCommit> commits = gitApi.log()
					.addRange(originMaster, gitApi.getRepository().resolve("refs/heads/" + username))
					.call();

			for (RevCommit commit : commits) {
				result.add(
						new CommitInfo(commit.getId().getName(), commit.getFullMessage(), commit.getAuthorIdent().getWhen())
				);
			}

			return result;
		}
		catch (GitAPIException | IOException e) {
			throw new IOException("Failed to get our unpublished changes", e);
		}
	}

	@Override
	public List<CommitInfo> getTheirPublishedChanges() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getTheirPublishedChanges` on a detached instance");
		}

		try {
			List<CommitInfo> result = new ArrayList<>();

			if (gitApi.getRepository().resolve(Constants.HEAD) == null) {
				return result; // no HEAD -> new empty project, no commits yet
			}

			List<String> remoteBranches = getRemoteBranches();
			if (remoteBranches.isEmpty()) {
				return result; // project has never been synchronized
			}

			ObjectId originMaster = gitApi.getRepository().resolve("refs/remotes/origin/" + Constants.MASTER);
			if (originMaster == null) {
				return result; // can't find origin/master
			}

			Iterable<RevCommit> commits = gitApi.log()
					.addRange(gitApi.getRepository().resolve("refs/heads/" + username), originMaster)
					.call();

			for (RevCommit commit : commits) {
				result.add(
						new CommitInfo(commit.getId().getName(), commit.getFullMessage(), commit.getAuthorIdent().getWhen())
				);
			}

			return result;
		}
		catch (GitAPIException | IOException e) {
			throw new IOException("Failed to get their published changes", e);
		}
	}


	@Override // AutoCloseable
	public void close() {
		if (gitApi == null) {
			return;
		}

		// TODO: review this - whether the underlying Repository instance is closed apparently depends on how the Git instance (this.gitApi)
		//       was created (see the docstring for Git.close, but note that most of it was copied from AutoCloseable;
		//       also see related TODOs, search for "JGitRepoManager.close")
		// apparently JGit doesn't close Git's internal Repository instance on its close
		// we need to call the close method of the Repository explicitly to avoid open handles to pack files
		// see https://stackoverflow.com/questions/31764311/how-do-i-release-file-system-locks-after-cloning-repo-via-jgit
		// see maybe related https://bugs.eclipse.org/bugs/show_bug.cgi?id=439305
		gitApi.getRepository().close();

		gitApi.close();
		gitApi = null;
	}


	// deprecated methods that are only used by migration code and will be removed
	@Deprecated
	public File getRepositoryWorkTree() {
		if (!this.isAttached()) {
			return null;
		}

		return gitApi.getRepository().getWorkTree();
	}

	@Deprecated
	public CommitInfo getHeadCommit() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getHeadCommit` on a detached instance");
		}

		try {
			ObjectId headCommit = gitApi.getRepository().resolve(Constants.HEAD);

			if (headCommit != null) {
				Iterator<RevCommit> iterator = gitApi.log().add(headCommit).call().iterator();

				if (iterator.hasNext()) {
					RevCommit revCommit = iterator.next();
					return new CommitInfo(revCommit.getId().getName(), revCommit.getFullMessage(), revCommit.getAuthorIdent().getWhen());
				}
			}

			return null;
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to get HEAD commit", e);
		}
	}

	@Deprecated
	public List<String> getSubmodulePaths() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getSubmodulePaths` on a detached instance");
		}

		try {
			List<String> paths = new ArrayList<>();

			try (SubmoduleWalk submoduleWalk = SubmoduleWalk.forIndex(gitApi.getRepository())) {
				while (submoduleWalk.next()) {
					paths.add(submoduleWalk.getModulesPath());
				}

				return paths;
			}
		}
		catch (Exception e) {
			throw new IOException("Failed to get submodule paths", e);
		}
	}

	@Deprecated
	public String cloneWithSubmodules(String group, String uri, JGitCredentialsManager jGitCredentialsManager) throws IOException {
		return cloneWithSubmodules(group, uri, jGitCredentialsManager, 0, 0);
	}

	@Deprecated
	private String cloneWithSubmodules(
			String group,
			String uri,
			JGitCredentialsManager jGitCredentialsManager,
			int refreshCredentialsTryCount,
			int tryCount
	) throws IOException {
		if (isAttached()) {
			throw new IllegalStateException("Can't call `cloneWithSubmodules` on an attached instance");
		}

		String repositoryName = uri.substring(uri.lastIndexOf("/") + 1);
		if (repositoryName.endsWith(".git")) {
			repositoryName = repositoryName.substring(0, repositoryName.length() - 4);
		}

		File targetPath = Paths.get(getUserRepositoryBasePath().toURI())
				.resolve(group)
				.resolve(repositoryName)
				.toFile();

		try {
			CloneCommand cloneCommand = jGitCommandFactory.newCloneCommand().setURI(uri).setDirectory(targetPath);
			cloneCommand.setCredentialsProvider(jGitCredentialsManager.getCredentialsProvider());
			gitApi = cloneCommand.call();
			gitApi.submoduleInit().call();

			SubmoduleUpdateCommand submoduleUpdateCommand = jGitCommandFactory.newSubmoduleUpdateCommand(gitApi.getRepository());
			submoduleUpdateCommand.setCredentialsProvider(jGitCredentialsManager.getCredentialsProvider());
			submoduleUpdateCommand.call();
		}
		catch (GitAPIException e) {
			if (e instanceof TransportException && e.getMessage().contains("not authorized") && refreshCredentialsTryCount < 1) {
				// it's likely that the user is logged in using the username/password authentication method and that their
				// GitLab OAuth access token has expired - try to refresh credentials and retry the operation once
				jGitCredentialsManager.refreshTransientCredentials();
				return cloneWithSubmodules(group, uri, jGitCredentialsManager, refreshCredentialsTryCount + 1, tryCount);
			}

			if (e instanceof TransportException && e.getMessage().contains("authentication not supported") && tryCount < 3) {
				// sometimes GitLab refuses to accept the clone and returns this error message
				// subsequent clone attempts succeed however, so we retry the clone up to 3 times before giving up
				try {
					Thread.sleep(100L * (tryCount + 1));
				}
				catch (InterruptedException ignored) {}

				return cloneWithSubmodules(group, uri, jGitCredentialsManager, refreshCredentialsTryCount, tryCount + 1);
			}

			// give up, refreshing credentials didn't work, retries exhausted, or unexpected error
			throw new IOException(
					String.format("Failed to clone with submodules, tried %d times", tryCount + 1),
					e
			);
		}

		return targetPath.getName();
	}

	@Deprecated
	public void initAndUpdateSubmodules(JGitCredentialsManager jGitCredentialsManager, Set<String> submodules) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `initAndUpdateSubmodules` on a detached instance");
		}

		try {
			SubmoduleInitCommand submoduleInitCommand = gitApi.submoduleInit();
			submoduleInitCommand.call();

			if (!submodules.isEmpty()) {
				SubmoduleUpdateCommand submoduleUpdateCommand = jGitCommandFactory.newSubmoduleUpdateCommand(gitApi.getRepository());
				submodules.forEach(submoduleUpdateCommand::addPath);
				submoduleUpdateCommand.setCredentialsProvider(jGitCredentialsManager.getCredentialsProvider());
				submoduleUpdateCommand.call();
			}
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to init and update submodules", e);
		}
	}

	@Deprecated
	public void reAddSubmodule(File submodulePath, String uri, JGitCredentialsManager jGitCredentialsManager) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `reAddSubmodule` on a detached instance");
		}

		try {
			Path basePath = gitApi.getRepository().getWorkTree().toPath();
			Path absoluteSubmodulePath = Paths.get(submodulePath.getAbsolutePath());
			Path relativeSubmodulePath = basePath.relativize(absoluteSubmodulePath);
			// NB: Git doesn't understand Windows path separators (\) in the .gitmodules file
			String unixStyleRelativeSubmodulePath = FilenameUtils.separatorsToUnix(relativeSubmodulePath.toString());

			String[] submoduleDirectoryContents = submodulePath.list();
			if (submoduleDirectoryContents != null && submoduleDirectoryContents.length != 0) {
				// skip re-adding this submodule as its directory exists and contains files
				logger.warning(
						String.format("Not re-adding submodule %s as its directory exists and contains files", unixStyleRelativeSubmodulePath)
				);
				return;
			}

			File gitSubmodulesFile = new File(gitApi.getRepository().getWorkTree(), Constants.DOT_GIT_MODULES);
			FileBasedConfig gitSubmodulesConfig = new FileBasedConfig(null, gitSubmodulesFile, FS.DETECTED);
			gitSubmodulesConfig.load();
			gitSubmodulesConfig.unsetSection(ConfigConstants.CONFIG_SUBMODULE_SECTION, unixStyleRelativeSubmodulePath);
			gitSubmodulesConfig.save();

			StoredConfig repositoryConfig = gitApi.getRepository().getConfig();
			repositoryConfig.unsetSection(ConfigConstants.CONFIG_SUBMODULE_SECTION, unixStyleRelativeSubmodulePath);
			repositoryConfig.save();

			gitApi.rm()
					.setCached(true)
					.addFilepattern(unixStyleRelativeSubmodulePath)
					.call();

			FileUtils.deleteDirectory(absoluteSubmodulePath.toFile());

			SubmoduleAddCommand submoduleAddCommand = jGitCommandFactory.newSubmoduleAddCommand(gitApi.getRepository())
					.setURI(uri)
					.setPath(unixStyleRelativeSubmodulePath);
			submoduleAddCommand.setCredentialsProvider(jGitCredentialsManager.getCredentialsProvider());
			Repository repository = submoduleAddCommand.call();
			repository.close();
		}
		catch (GitAPIException | ConfigInvalidException e) {
			throw new IOException("Failed to re-add submodule", e);
		}
	}

	@Deprecated
	public void removeSubmodule(File submodulePath) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `removeSubmodule` on a detached instance");
		}

		try {
			Path basePath = gitApi.getRepository().getWorkTree().toPath();
			Path absoluteSubmodulePath = Paths.get(submodulePath.getAbsolutePath());
			Path relativeSubmodulePath = basePath.relativize(absoluteSubmodulePath);
			String unixStyleRelativeSubmodulePath = FilenameUtils.separatorsToUnix(relativeSubmodulePath.toString());

			File gitSubmodulesFile = new File(gitApi.getRepository().getWorkTree(), Constants.DOT_GIT_MODULES );
			FileBasedConfig gitSubmodulesConfig = new FileBasedConfig(null, gitSubmodulesFile, FS.DETECTED);
			gitSubmodulesConfig.load();
			gitSubmodulesConfig.unsetSection(ConfigConstants.CONFIG_SUBMODULE_SECTION, unixStyleRelativeSubmodulePath);
			gitSubmodulesConfig.save();

			StoredConfig repositoryConfig = gitApi.getRepository().getConfig();
			repositoryConfig.unsetSection(ConfigConstants.CONFIG_SUBMODULE_SECTION, unixStyleRelativeSubmodulePath);
			repositoryConfig.save();

			gitApi.add()
					.addFilepattern(Constants.DOT_GIT_MODULES)
					.call();

			gitApi.rm().setCached(true).addFilepattern(unixStyleRelativeSubmodulePath).call();

			detach();

			File submoduleGitDir = basePath
					.resolve(Constants.DOT_GIT)
					.resolve(Constants.MODULES)
					.resolve(relativeSubmodulePath).toFile();

			FileUtils.deleteDirectory(submoduleGitDir);

			FileUtils.deleteDirectory(absoluteSubmodulePath.toFile());
		}
		catch (GitAPIException | ConfigInvalidException e) {
			throw new IOException("Failed to remove submodule", e);
		}
	}

	@Deprecated
	public boolean hasRef(String branch) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `hasRef` on a detached instance");
		}

		return gitApi.getRepository().findRef(branch) != null;
	}

	@Deprecated
	public void checkoutNewFromBranch(String name, String baseBranchName) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `checkoutNewFromBranch` on a detached instance");
		}

		try {
			if (gitApi.getRepository().getBranch().equals(name)) {
				// already the active branch
				return;
			}

			CheckoutCommand checkoutCommand = gitApi.checkout();

			// set the start point for the checkout to baseBranch, if we can
			try (RevWalk revWalk = new RevWalk(gitApi.getRepository())) {
				ObjectId baseBranch = gitApi.getRepository().resolve("refs/heads/" + baseBranchName);
				// can be null if a project is still empty - in that case branch creation will fail,
				// because JGit cannot create a branch without a start point
				if (baseBranch != null) {
					RevCommit baseBranchHeadCommit = revWalk.parseCommit(baseBranch);
					checkoutCommand.setStartPoint(baseBranchHeadCommit);
				}
			}

			checkoutCommand.setCreateBranch(true);
			checkoutCommand.setName(name).call();

			// update config to set 'remote' and 'merge' (upstream branch) for this branch
			StoredConfig config = gitApi.getRepository().getConfig();
			config.setString(
					ConfigConstants.CONFIG_BRANCH_SECTION,
					name,
					ConfigConstants.CONFIG_KEY_REMOTE,
					"origin"
			);
			config.setString(
					ConfigConstants.CONFIG_BRANCH_SECTION,
					name,
					ConfigConstants.CONFIG_KEY_MERGE,
					"refs/heads/" + name
			);
			config.save();
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to checkout", e);
		}
	}

	@Deprecated
	public void checkoutFromOrigin(String name) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `checkoutFromOrigin` on a detached instance");
		}

		try {
			if (gitApi.getRepository().getBranch().equals(name)) {
				// already the active branch
				return;
			}

			CheckoutCommand checkoutCommand = gitApi.checkout();

			boolean createBranch = true;

			// check if the branch already exists
			List<Ref> refs = gitApi.branchList().call();
			boolean branchAlreadyExists = refs.stream()
					.map(Ref::getName)
					.anyMatch(refName -> refName.equals("refs/heads/" + name));

			if (branchAlreadyExists) {
				createBranch = false;
			}
			else {
				// set the start point for the checkout to origin/<name>
				try (RevWalk revWalk = new RevWalk(gitApi.getRepository())) {
					ObjectId branch = gitApi.getRepository().resolve("refs/remotes/origin/" + name);
					RevCommit branchHeadCommit = revWalk.parseCommit(branch);
					checkoutCommand.setStartPoint(branchHeadCommit);
				}
			}

			checkoutCommand.setCreateBranch(createBranch);
			checkoutCommand.setName(name).call();

			if (createBranch) {
				// update config to set 'remote' and 'merge' (upstream branch) for this branch
				StoredConfig config = gitApi.getRepository().getConfig();
				config.setString(
						ConfigConstants.CONFIG_BRANCH_SECTION,
						name,
						ConfigConstants.CONFIG_KEY_REMOTE,
						"origin"
				);
				config.setString(
						ConfigConstants.CONFIG_BRANCH_SECTION,
						name,
						ConfigConstants.CONFIG_KEY_MERGE,
						"refs/heads/" + name
				);
				config.save();
			}
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to checkout", e);
		}
	}

	/**
	 * Pushes commits made locally on the currently checked out branch to the associated remote ('origin') repository and branch.
	 * <p>
	 * Skips branch checks - only to be used for project migration from CATMA 6 -> 7.
	 *
	 * @param jGitCredentialsManager a {@link JGitCredentialsManager} to use for authentication
	 * @return a {@link List<PushResult>} containing the results of the push operation
	 * @throws IOException if an error occurs when pushing
	 */
	@Deprecated
	public List<PushResult> pushWithoutBranchChecks(JGitCredentialsManager jGitCredentialsManager) throws IOException {
		return push(jGitCredentialsManager, null, true, 0, 0);
	}

	@Deprecated
	public boolean hasRemoteRef(String branch) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `hasRemoteRef` on a detached instance");
		}

		return gitApi.getRepository().resolve("refs/remotes/" + branch) != null;
	}

	@Deprecated
	public void remoteAdd(String name, String uri) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `remoteAdd` on a detached instance");
		}

		try {
			RemoteAddCommand remoteAddCommand = gitApi.remoteAdd();
			remoteAddCommand.setName(name);
			remoteAddCommand.setUri(new URIish(uri));
			remoteAddCommand.call();
		}
		catch (GitAPIException | URISyntaxException e) {
			throw new IOException("Failed to add remote", e);
		}
	}

	@Deprecated
	public void remoteRemove(String name) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `remoteRemove` on a detached instance");
		}

		try {
			RemoteRemoveCommand remoteRemoveCommand = gitApi.remoteRemove();
			remoteRemoveCommand.setName(name);
			remoteRemoveCommand.call();
		}
		catch (GitAPIException e) {
			throw new IOException("Failed to remove remote", e);
		}
	}

	@Deprecated
	public boolean resolveRootConflicts(JGitCredentialsManager jGitCredentialsManager) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `resolveRootConflicts` on a detached instance");
		}

		DirCache dirCache = null;

		try {
			dirCache = gitApi.getRepository().lockDirCache();

			if (!dirCache.hasUnmergedPaths()) {
				return true;
			}

			Set<String> unresolved = new HashSet<>();

			Status status = gitApi.status().call();

			for (String conflictingSubmodule : status.getConflicting()) {
				StageState conflictState = status.getConflictingStageState().get(conflictingSubmodule);

				switch (conflictState) {
					case BOTH_MODIFIED: {
						// get the base entry from where the branches diverge, the common ancestor version
						int baseIdx = dirCache.findEntry(conflictingSubmodule);
						DirCacheEntry baseEntry = dirCache.getEntry(baseIdx);

						// get their version, the being-merged-in version
						DirCacheEntry theirEntry = dirCache.getEntry(baseIdx + 2);

						// Stage 0: 'normal', un-conflicted, all-is-well entry.
						// Stage 1: 'base', the common ancestor version.
						// Stage 2: 'ours', the target (HEAD) version.
						// Stage 3: 'theirs', the being-merged-in version.

						if (theirEntry.getPathString().equals(conflictingSubmodule) && theirEntry.getStage() == 3) {
							// we try to make sure that their version is included (merged) in the latest version of this submodule
							ensureLatestSubmoduleRevision(baseEntry, theirEntry, conflictingSubmodule, jGitCredentialsManager);

							try (Repository submoduleRepo = SubmoduleWalk.getSubmoduleRepository(gitApi.getRepository(), conflictingSubmodule)) {
								// now get the current submodule revision (which includes the merge)
								ObjectId submoduleHeadRevision = submoduleRepo.resolve(Constants.HEAD);
								baseEntry.setObjectId(submoduleHeadRevision);
							}
						}
						else {
							logger.severe(
									String.format(
											"Cannot resolve root conflict for submodule %s! Expected a 'theirs'-stage-3 commit entry but found none.",
											conflictingSubmodule
									)
							);
							unresolved.add(conflictingSubmodule);
						}
						break;
					}
					case DELETED_BY_THEM: {
						unresolved.add(conflictingSubmodule);

						String ourTreeName = "refs/heads/master";
						RevCommit ourCommit = gitApi.log()
								.add(gitApi.getRepository().resolve(ourTreeName))
								.addPath(conflictingSubmodule)
								.call().iterator().next();
						String ourLastCommitMsg = ourCommit.getFullMessage();

						String theirTreeName = "refs/remotes/origin/master";
						RevCommit theirCommit = gitApi.log()
								.add(gitApi.getRepository().resolve(theirTreeName))
								.addPath(conflictingSubmodule)
								.call().iterator().next();

						if (theirCommit == null) {
							// couldn't find their commit based on the conflicting submodule path
							// we try to find it based on the DOT_GIT_MODULES file and the resource ID in the commit message
							Iterator<RevCommit> remoteCommitIterator = gitApi.log()
									.add(gitApi.getRepository().resolve(theirTreeName))
									.addPath(Constants.DOT_GIT_MODULES)
									.call().iterator();

							String resourceId = conflictingSubmodule.substring(conflictingSubmodule.indexOf('/') + 1);

							while (remoteCommitIterator.hasNext()) {
								RevCommit revCommit = remoteCommitIterator.next();
								if (revCommit.getFullMessage().contains(resourceId)) {
									theirCommit = revCommit;
									break;
								}
							}
						}

						String theirLastCommitMsg = theirCommit == null ? "no commit found" : theirCommit.getFullMessage();

						logger.severe(
								String.format(
										"Cannot resolve root conflict DELETED_BY_THEM in submodule %1$s " +
												"with our commit %2$s '%3$s' " +
												"and their commit %4$s '%5$s'",
										conflictingSubmodule,
										ourCommit.getName(),
										ourLastCommitMsg,
										theirCommit == null ? "n/a" : theirCommit.getName(),
										theirLastCommitMsg
								)
						);
						break;
					}
					case DELETED_BY_US: {
						unresolved.add(conflictingSubmodule);

						String ourTreeName = "refs/heads/master";
						RevCommit ourCommit = gitApi.log()
								.add(gitApi.getRepository().resolve(ourTreeName))
								.addPath(conflictingSubmodule)
								.call().iterator().next();
						String ourLastCommitMsg = ourCommit.getFullMessage();

						String theirTreeName = "refs/remotes/origin/master";
						RevCommit theirCommit = gitApi.log()
								.add(gitApi.getRepository().resolve(theirTreeName))
								.addPath(conflictingSubmodule)
								.call().iterator().next();
						String theirLastCommitMsg = theirCommit.getFullMessage();

						logger.severe(
								String.format(
										"Cannot resolve root conflict DELETED_BY_US in submodule %1$s " +
												"with our commit %2$s '%3$s' " +
												"and their commit %4$s '%5$s'",
										conflictingSubmodule,
										ourCommit.getName(),
										ourLastCommitMsg,
										theirCommit.getName(),
										theirLastCommitMsg
								)
						);
						break;
					}
					default: {
						unresolved.add(conflictingSubmodule);

						logger.severe(
								String.format(
										"Cannot resolve root conflict for submodule %s! %s not supported yet.",
										conflictingSubmodule,
										conflictState.name()
								)
						);
					}
				}
			}

			dirCache.write();
			dirCache.commit();

			return unresolved.isEmpty();
		}
		catch (Exception e) {
			throw new IOException("Failed to resolve root conflicts", e);
		}
		finally {
			if (dirCache != null) {
				dirCache.unlock();
			}
		}
	}

	@Deprecated
	private void ensureLatestSubmoduleRevision(
			DirCacheEntry baseEntry,
			DirCacheEntry theirEntry,
			String conflictingSubmodule,
			JGitCredentialsManager jGitCredentialsManager
	) throws Exception {
		try (Repository submoduleRepo = SubmoduleWalk.getSubmoduleRepository(gitApi.getRepository(), conflictingSubmodule)) {
			Git submoduleGitApi = Git.wrap(submoduleRepo);

			boolean foundTheirs = false;
			int tries = 10;

			while (!foundTheirs && tries > 0) {
				// iterate over the revisions until we find their commit or until we reach the common ancestor version (base)
				for (RevCommit revCommit : submoduleGitApi.log().call()) {
					if (revCommit.getId().equals(theirEntry.getObjectId())) {
						// we found their version
						foundTheirs = true;
						break;
					}
					else if (revCommit.getId().equals(baseEntry.getObjectId())) {
						// we reached the common ancestor
						break;
					}
				}

				if (!foundTheirs) {
					// we reached the common ancestor without finding their commit, so we pull again and start over
					// (next iteration of while loop) to see if it comes in now
					submoduleGitApi.checkout()
							.setName(Constants.MASTER)
							.setCreateBranch(false)
							.call();

					PullResult pullResult = submoduleGitApi.pull().setCredentialsProvider(jGitCredentialsManager.getCredentialsProvider()).call();
					if (!pullResult.isSuccessful()) {
						throw new IOException(
								String.format("Failed to get the latest commits for submodule %s", conflictingSubmodule)
						);
					}

					submoduleGitApi.checkout()
							.setName(LocalGitRepositoryManager.DEFAULT_LOCAL_DEV_BRANCH)
							.setCreateBranch(false)
							.call();

					submoduleGitApi.rebase().setUpstream(Constants.MASTER).call();
				}

				tries--;
			}

			if (!foundTheirs) {
				logger.severe(
						String.format(
								"Cannot resolve root conflict for submodule %s! Commit %s is missing.",
								conflictingSubmodule,
								theirEntry.getObjectId().toString()
						)
				);
				throw new CommitMissingException(
						"Failed to synchronize the project because of a missing commit, try again later or contact support."
				);
			}
		}
	}

	@Deprecated
	public void resolveGitSubmoduleFileConflicts() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `resolveGitSubmoduleFileConflicts` on a detached instance");
		}

		DirCache dirCache = null;

		try {
			dirCache = gitApi.getRepository().lockDirCache();

			// get base version
			int baseIdx = dirCache.findEntry(Constants.DOT_GIT_MODULES);
			DirCacheEntry baseEntry = dirCache.getEntry(baseIdx);

			Config ourConfig = null;
			Config theirConfig = null;

			Set<String> baseModules = null;
			Set<String> ourModules = null;
			Set<String> theirModules = null;

			if (baseEntry.getStage() == DirCacheEntry.STAGE_1) {
				// get our version
				DirCacheEntry ourEntry = dirCache.getEntry(baseIdx + 1);
				// get their version, the being-merged-in version
				DirCacheEntry theirEntry = dirCache.getEntry(baseIdx + 2);

				Config baseConfig = getDotGitModulesConfig(baseEntry.getObjectId());
				ourConfig = getDotGitModulesConfig(ourEntry.getObjectId());
				theirConfig = getDotGitModulesConfig(theirEntry.getObjectId());

				baseModules = baseConfig.getSubsections(ConfigConstants.CONFIG_SUBMODULE_SECTION);
				ourModules = ourConfig.getSubsections(ConfigConstants.CONFIG_SUBMODULE_SECTION);
				theirModules = theirConfig.getSubsections(ConfigConstants.CONFIG_SUBMODULE_SECTION);
			}
			else if (baseEntry.getStage() == DirCacheEntry.STAGE_2) { // no common ancestor
				DirCacheEntry ourEntry = baseEntry;
				DirCacheEntry theirEntry = dirCache.getEntry(baseIdx + 1);

				ourConfig = getDotGitModulesConfig(ourEntry.getObjectId());
				theirConfig = getDotGitModulesConfig(theirEntry.getObjectId());

				baseModules = new HashSet<>();
				ourModules = ourConfig.getSubsections(ConfigConstants.CONFIG_SUBMODULE_SECTION);
				theirModules = theirConfig.getSubsections(ConfigConstants.CONFIG_SUBMODULE_SECTION);
			}

			for (String name : theirModules) {
				if (!ourModules.contains(name)) {
					if (baseModules.contains(name)) {
						// deleted by us
					}
					else {
						// added by them
						ourConfig.setString(
								ConfigConstants.CONFIG_SUBMODULE_SECTION,
								name,
								"path",
								theirConfig.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, name, "path")
						);
						ourConfig.setString(
								ConfigConstants.CONFIG_SUBMODULE_SECTION,
								name,
								"url",
								theirConfig.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, name, "url")
						);
					}
				}
			}

			for (String name : ourModules) {
				if (!theirModules.contains(name)) {
					if (baseModules.contains(name)) {
						// deleted by them
						ourConfig.unsetSection(ConfigConstants.CONFIG_SUBMODULE_SECTION, name);
					}
					else {
						// added by us
					}
				}
			}

			dirCache.unlock();

			add(
					new File(gitApi.getRepository().getWorkTree(), Constants.DOT_GIT_MODULES),
					ourConfig.toText().getBytes(StandardCharsets.UTF_8)
			);
		}
		catch (Exception e) {
			throw new IOException("Failed to resolve .gitmodules conflicts", e);
		}
		finally {
			if (dirCache != null) {
				dirCache.unlock();
			}
		}
	}

	@Deprecated
	private Config getDotGitModulesConfig(ObjectId objectId) throws Exception {
		ObjectLoader loader = gitApi.getRepository().open(objectId);
		String content = new String(loader.getBytes(), StandardCharsets.UTF_8);

		Config config = new Config();
		config.fromText(content);
		return config;
	}

	@Deprecated
	public List<CommitInfo> getCommitsNeedToBeMergedFromDevToMaster() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getCommitsNeedsToBeMergedFromDevToMaster` on a detached instance");
		}

		try {
			List<CommitInfo> result = new ArrayList<>();

			if (gitApi.getRepository().resolve(Constants.HEAD) == null) {
				return result; // no HEAD -> new empty project, no commits yet
			}

			Iterable<RevCommit> commits = Collections.emptyList();

			ObjectId master = gitApi.getRepository().resolve("refs/heads/master");
			ObjectId dev = gitApi.getRepository().resolve("refs/heads/dev");

			if (dev != null) {
				if (master != null) {
					commits = this.gitApi.log().addRange(master, dev).call();
				}
				else {
					commits = this.gitApi.log().call();
				}
			}

			for (RevCommit commit : commits) {
				result.add(
						new CommitInfo(commit.getId().getName(), commit.getFullMessage(), commit.getAuthorIdent().getWhen())
				);
			}

			return result;
		}
		catch (GitAPIException | IOException e) {
			throw new IOException("Failed to get commits needing to be merged from dev to master", e);
		}
	}

	@Deprecated
	public List<CommitInfo> getCommitsNeedToBeMergedFromMasterToOriginMaster() throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getCommitsNeedToBeMergedFromMasterToOriginMaster` on a detached instance");
		}

		try {
			List<CommitInfo> result = new ArrayList<>();

			if (gitApi.getRepository().resolve(Constants.HEAD) == null) {
				return result; // no HEAD -> new empty project, no commits yet
			}

			List<String> remoteBranches = getRemoteBranches();
			ObjectId originMaster = gitApi.getRepository().resolve("refs/remotes/origin/" + Constants.MASTER);

			Iterable<RevCommit> commits;

			if (remoteBranches.isEmpty() || originMaster == null) {
				// project has never been synchronized or can't find origin/master
				commits = gitApi.log().call();
			}
			else {
				commits = gitApi.log()
						.addRange(originMaster, gitApi.getRepository().resolve("refs/heads/" + Constants.MASTER))
						.call();
			}

			for (RevCommit commit : commits) {
				result.add(
						new CommitInfo(commit.getId().getName(), commit.getFullMessage(), commit.getAuthorIdent().getWhen())
				);
			}

			return result;
		}
		catch (GitAPIException | IOException e) {
			throw new IOException("Failed to get commits needing to be merged from master to origin/master", e);
		}
	}

	@Deprecated
	public List<CommitInfo> getCommitsNeedToBeMergedFromC6MigrationToOriginC6Migration(String migrationBranchName) throws IOException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `getCommitsNeedToBeMergedFromC6MigrationToOriginC6Migration` on a detached instance");
		}

		try {
			List<CommitInfo> result = new ArrayList<>();

			if (gitApi.getRepository().resolve(Constants.HEAD) == null) {
				return result; // no HEAD -> new empty project, no commits yet
			}

			List<String> remoteBranches = getRemoteBranches();
			ObjectId originMigrationBranch = gitApi.getRepository().resolve("refs/remotes/origin/" + migrationBranchName);

			Iterable<RevCommit> commits;

			if (remoteBranches.isEmpty() || originMigrationBranch == null) {
				// project has never been synchronized or can't find origin/<migrationBranchName>
				commits = gitApi.log().call();
			}
			else {
				commits = gitApi.log()
						.addRange(originMigrationBranch, gitApi.getRepository().resolve("refs/heads/" + migrationBranchName))
						.call();
			}

			for (RevCommit commit : commits) {
				result.add(
						new CommitInfo(commit.getId().getName(), commit.getFullMessage(), commit.getAuthorIdent().getWhen())
				);
			}

			return result;
		}
		catch (GitAPIException | IOException e) {
			throw new IOException(
					String.format("Failed to get commits needing to be merged from %1$s to origin/%1$s", migrationBranchName),
					e
			);
		}
	}
} 
