package com.fastaccess.ui.modules.repos;

import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.fastaccess.R;
import com.fastaccess.data.dao.model.Login;
import com.fastaccess.data.dao.model.Repo;
import com.fastaccess.helper.AppHelper;
import com.fastaccess.helper.InputHelper;
import com.fastaccess.helper.Logger;
import com.fastaccess.helper.RxHelper;
import com.fastaccess.provider.rest.RestProvider;
import com.fastaccess.ui.base.mvp.presenter.BasePresenter;
import com.fastaccess.ui.modules.repos.code.RepoCodePagerView;
import com.fastaccess.ui.modules.repos.issues.RepoIssuesPagerView;
import com.fastaccess.ui.modules.repos.pull_requests.RepoPullRequestPagerView;

import static com.fastaccess.helper.ActivityHelper.getVisibleFragment;

/**
 * Created by Kosh on 09 Dec 2016, 4:17 PM
 */

class RepoPagerPresenter extends BasePresenter<RepoPagerMvp.View> implements RepoPagerMvp.Presenter {
    private boolean isWatched;
    private boolean isStarred;
    private boolean isForked;
    private final String login;
    private final String repoId;
    private Repo repo;
    private int navTyp;

    RepoPagerPresenter(final String repoId, final String login, @RepoPagerMvp.RepoNavigationType int navTyp) {
        if (!InputHelper.isEmpty(login) && !InputHelper.isEmpty(repoId())) {
            throw new IllegalArgumentException("arguments cannot be empty");
        }
        this.repoId = repoId;
        this.login = login;
        this.navTyp = navTyp;
    }

    private void callApi(int navTyp) {
        if (InputHelper.isEmpty(login) || InputHelper.isEmpty(repoId)) return;
        Logger.e(navTyp);
        makeRestCall(RestProvider.getRepoService().getRepo(login(), repoId()),
                repoModel -> {
                    this.repo = repoModel;
                    manageSubscription(this.repo.save(repo).subscribe());
                    sendToView(view -> {
                        view.onInitRepo();
                        view.onNavigationChanged(navTyp);
                    });
                    onCheckStarring();
                    onCheckWatching();
                });
    }

    @Override public void onError(@NonNull Throwable throwable) {
        onWorkOffline();
        super.onError(throwable);
    }

    @Override protected void onAttachView(final @NonNull RepoPagerMvp.View view) {
        super.onAttachView(view);
        if (getRepo() != null) {
            view.onInitRepo();
        } else {
            callApi(navTyp);
        }
    }

    @NonNull @Override public String repoId() {
        return repoId;
    }

    @NonNull @Override public String login() {
        return login;
    }

    @Nullable @Override public Repo getRepo() {
        return repo;
    }

    @Override public boolean isWatched() {
        return isWatched;
    }

    @Override public boolean isStarred() {
        return isStarred;
    }

    @Override public boolean isForked() {
        return isForked;
    }

    @Override public boolean isRepoOwner() {
        return (getRepo() != null && getRepo().getOwner() != null) && getRepo().getOwner().getLogin().equals(Login.getUser().getLogin());
    }

    @Override public void onWatch() {
        if (getRepo() == null) return;
        isWatched = !isWatched;
        sendToView(view -> {
            view.onRepoWatched(isWatched);
            view.onChangeWatchedCount(isWatched);
        });
    }

    @Override public void onStar() {
        if (getRepo() == null) return;
        isStarred = !isStarred;
        sendToView(view -> {
            view.onRepoStarred(isStarred);
            view.onChangeStarCount(isStarred);
        });
    }

    @Override public void onFork() {
        if (!isForked && getRepo() != null) {
            isForked = true;
            sendToView(view -> {
                view.onRepoForked(isForked);
                view.onChangeForkCount(isForked);
            });
        }
    }

    @Override public void onCheckWatching() {
        if (getRepo() != null) {
            String login = login();
            String name = repoId();
            manageSubscription(RxHelper.getObserver(RestProvider.getRepoService().isWatchingRepo(login, name))
                    .doOnSubscribe(() -> sendToView(view -> view.onEnableDisableWatch(false)))
                    .doOnNext(subscriptionModel -> sendToView(view -> view.onRepoWatched(isWatched = subscriptionModel.isSubscribed())))
                    .onErrorReturn(throwable -> {
                        isWatched = false;
                        sendToView(view -> view.onRepoWatched(isWatched));
                        return null;
                    })
                    .subscribe());
        }
    }

    @Override public void onCheckStarring() {
        if (getRepo() != null) {
            String login = login();
            String name = repoId();
            manageSubscription(RxHelper.getObserver(RestProvider.getRepoService().checkStarring(login, name))
                    .doOnSubscribe(() -> sendToView(view -> view.onEnableDisableStar(false)))
                    .doOnNext(response -> sendToView(view -> view.onRepoStarred(isStarred = response.code() == 204)))
                    .onErrorReturn(throwable -> {
                        isStarred = false;
                        sendToView(view -> view.onRepoStarred(isStarred));
                        return null;
                    })
                    .subscribe());
        }
    }

    @Override public void onWorkOffline() {
        if (!InputHelper.isEmpty(login()) && !InputHelper.isEmpty(repoId())) {
            Logger.e(login, repoId);
            manageSubscription(RxHelper.getObserver(Repo.getRepo(repoId))
                    .subscribe(repoModel -> {
                        repo = repoModel;
                        if (repo != null) {
                            sendToView(view -> {
                                view.onInitRepo();
                                view.onNavigationChanged(RepoPagerMvp.CODE);
                            });
                        } else {
                            callApi(navTyp);
                        }
                    }));
        } else {
            sendToView(RepoPagerMvp.View::onFinishActivity);
        }
    }

    @Override public void onModuleChanged(@NonNull FragmentManager fragmentManager, @RepoPagerMvp.RepoNavigationType int type) {
        Fragment currentVisible = getVisibleFragment(fragmentManager);
        RepoCodePagerView codePagerView = (RepoCodePagerView) AppHelper.getFragmentByTag(fragmentManager, RepoCodePagerView.TAG);
        RepoIssuesPagerView repoIssuesPagerView = (RepoIssuesPagerView) AppHelper.getFragmentByTag(fragmentManager, RepoIssuesPagerView.TAG);
        RepoPullRequestPagerView pullRequestPagerView = (RepoPullRequestPagerView) AppHelper.getFragmentByTag(fragmentManager,
                RepoPullRequestPagerView.TAG);
        if (getRepo() == null) {
            sendToView(RepoPagerMvp.View::onFinishActivity);
            return;
        }
        if (currentVisible == null) return;
        Logger.e(currentVisible);
        switch (type) {
            case RepoPagerMvp.CODE:
                if (codePagerView == null) {
                    onAddAndHide(fragmentManager, RepoCodePagerView.newInstance(repoId(), login(),
                            getRepo().getUrl(), getRepo().getDefaultBranch()), currentVisible);
                } else {
                    onShowHideFragment(fragmentManager, codePagerView, currentVisible);
                }
                break;
            case RepoPagerMvp.ISSUES:
                if ((!getRepo().isHasIssues())) {
                    sendToView(view -> view.showMessage(R.string.error, R.string.no_issue));
                    return;
                }
                if (repoIssuesPagerView == null) {
                    onAddAndHide(fragmentManager, RepoIssuesPagerView.newInstance(repoId(), login()), currentVisible);
                } else {
                    onShowHideFragment(fragmentManager, repoIssuesPagerView, currentVisible);
                }
                break;
            case RepoPagerMvp.PULL_REQUEST:
                if (pullRequestPagerView == null) {
                    onAddAndHide(fragmentManager, RepoPullRequestPagerView.newInstance(repoId(), login()), currentVisible);
                } else {
                    onShowHideFragment(fragmentManager, pullRequestPagerView, currentVisible);
                }
                break;
        }
    }

    @Override public void onShowHideFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment toShow, @NonNull Fragment toHide) {
        Logger.e(toShow, toHide);
        fragmentManager
                .beginTransaction()
                .hide(toHide)
                .show(toShow)
                .commit();
        toHide.onHiddenChanged(true);
        toShow.onHiddenChanged(false);
    }

    @Override public void onAddAndHide(@NonNull FragmentManager fragmentManager, @NonNull Fragment toAdd, @NonNull Fragment toHide) {
        fragmentManager
                .beginTransaction()
                .hide(toHide)
                .add(R.id.container, toAdd, toAdd.getClass().getSimpleName())
                .commit();
        toHide.onHiddenChanged(true);
        toAdd.onHiddenChanged(false);
    }

    @Override public void onDeleteRepo() {
        if (isRepoOwner()) {
            makeRestCall(RestProvider.getRepoService().deleteRepo(login, repoId),
                    booleanResponse -> {
                        if (booleanResponse.code() == 204) {
//                            if (repo != null) repo.delete().execute();
                            repo = null;
                            sendToView(RepoPagerMvp.View::onInitRepo);
                        }
                    });
        }
    }

    @Override public void onMenuItemSelect(@IdRes int id, int position, boolean fromUser) {
        if (id == R.id.issues && (getRepo() != null && !getRepo().isHasIssues())) {
            return;
        }
        if (getView() != null && isViewAttached()) {
            getView().onNavigationChanged(position);
        }
    }

    @Override public void onMenuItemReselect(@IdRes int id, int position, boolean fromUser) {}
}
