package de.catma.ui.login;

import java.io.IOException;

import com.google.inject.Inject;
import com.vaadin.server.VaadinSession;

import de.catma.repository.git.GitUser;
import de.catma.repository.git.interfaces.IRemoteGitManagerPrivileged;
import de.catma.repository.git.interfaces.IRemoteGitManagerRestricted;
import de.catma.repository.git.managers.GitlabManagerPrivileged;
import de.catma.ui.di.IRemoteGitManagerFactory;
import de.catma.util.Pair;

public class LocalUserLoginService implements LoginService {

	private IRemoteGitManagerRestricted api;
	
	
	private final IRemoteGitManagerFactory iRemoteGitManagerFactory;
		
	@Inject
	public LocalUserLoginService(IRemoteGitManagerFactory iRemoteGitManagerFactory) {
		this.iRemoteGitManagerFactory=iRemoteGitManagerFactory;
	}
	
	@Override
	public IRemoteGitManagerRestricted login(String username, String password) throws IOException {
		String user = System.getProperty("user.name");
		Pair<GitUser, String> userTokenPair = new GitlabManagerPrivileged().acquireImpersonationToken(
				user, "catma", user+"@catma.de",user);
		api = iRemoteGitManagerFactory.createFromToken(userTokenPair.getSecond());
		return api;
	}

	@Override
	public IRemoteGitManagerRestricted loggedInFromThirdParty(String identifier, String provider, String email, String name) throws IOException {
		IRemoteGitManagerPrivileged gitlabManagerPrivileged = new GitlabManagerPrivileged();		
        Pair<GitUser, String> userAndToken = 
        		gitlabManagerPrivileged.acquireImpersonationToken(identifier, provider, email, name);

		api = iRemoteGitManagerFactory.createFromToken(userAndToken.getSecond());
		return api;
	}

	@Override
	public IRemoteGitManagerRestricted getAPI() {
		if(this.api == null){
			try {
				api = login("","");
			} catch (IOException e) {
				api = null; //NOOP 
			}
		}
		return api;
	}

	@Override
	public void logout() {
		VaadinSession.getCurrent().close();
		api = null;
    	VaadinSession.getCurrent().getSession().invalidate();
	}
}
