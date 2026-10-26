/** 关注 / 粉丝列表面板（个人主页与他人主页共用） */

import { api } from '../api.js';
import { isFollowing, setFollowing } from '../local.js';
import { auth } from '../store.js';
import {
  bindPagination,
  emptyState,
  errorState,
  followButton,
  pagination,
  skeletonRows,
  toast,
  userRow,
} from '../ui.js';
import { isAbortError } from '../utils.js';

export function createRelationsPanel({ container, userId, signal, onTotal }) {
  let tab = 'following';
  let page = 1;
  let seq = 0;

  const isSelf = () =>
    Boolean(auth.me?.id) && String(auth.me.id) === String(userId);

  async function load() {
    const current = ++seq;
    container.innerHTML = skeletonRows(4);

    try {
      const res =
        tab === 'following'
          ? await api.followingList(userId, page, { signal })
          : await api.fansList(userId, page, { signal });

      if (current !== seq || signal.aborted) return;

      onTotal?.(tab, Number(res?.totalCount) || 0);
      render(res);
    } catch (err) {
      if (isAbortError(err) || signal.aborted) return;
      container.innerHTML = errorState({
        error: err,
        title: '列表加载失败',
        retryId: 'relations-retry',
      });
      container.querySelector('#relations-retry')?.addEventListener('click', load);
    }
  }

  function render(res) {
    const list = Array.isArray(res?.data) ? res.data : [];
    const total = Number(res?.totalCount) || 0;
    const totalPage = Number(res?.totalPage) || 1;
    const pageNo = Number(res?.pageNo) || page;
    const isOwnFollowing = isSelf() && tab === 'following';

    if (isOwnFollowing) {
      list.forEach((user) => {
        const targetId = String(user.userId ?? user.id ?? '');
        if (targetId) setFollowing(targetId, true);
      });
    }
    const emptyText =
      tab === 'following'
        ? { title: '还没有关注任何人', desc: '去搜索用户，找到感兴趣的创作者关注吧。', icon: 'users' }
        : { title: '还没有粉丝', desc: '发布有趣的笔记，让更多人发现你。', icon: 'users' };

    if (!list.length) {
      container.innerHTML = emptyState({
        ...emptyText,
        actions: [{ label: '去搜索用户', href: '#/search?tab=users', variant: 'btn-primary' }],
      });
      return;
    }

    container.innerHTML = `<div class="user-list">
      ${list
        .map((user) => {
          const targetId = String(user.userId ?? user.id ?? '');
          const action =
            targetId === String(auth.me?.id)
              ? '<span class="badge badge-outline">自己</span>'
              : followButton(targetId, isOwnFollowing || isFollowing(targetId));
          return userRow(user, { actionHtml: action });
        })
        .join('')}
    </div>
    ${pagination({ pageNo, totalPage, totalCount: total, id: 'relations-pager' })}`;

    bindPagination(container.querySelector('#relations-pager'), (nextPage) => {
      page = nextPage;
      load();
    });

    container.querySelectorAll('[data-follow-user]').forEach((button) => {
      button.addEventListener('click', () => handleFollow(button));
    });
  }

  async function handleFollow(button) {
    const targetId = button.dataset.followUser;
    if (!targetId) return;

    const followed = isFollowing(targetId);
    button.disabled = true;

    try {
      if (followed) await api.unfollow(targetId, { signal });
      else await api.follow(targetId, { signal });

      setFollowing(targetId, !followed);
      toast(followed ? '已取消关注' : '关注成功', 'success');

      if (isSelf() && tab === 'following' && followed) {
        // 从「我的关注」列表取关后重新拉取，让该用户消失
        load();
      } else {
        paint(button, !followed);
      }
    } catch (err) {
      if (isAbortError(err) || signal.aborted) return;

      if (err.code === 'RELATION-20004') {
        setFollowing(targetId, true);
        paint(button, true);
        toast('已经关注过该用户', 'info');
      } else if (err.code === 'RELATION-20006') {
        setFollowing(targetId, false);
        paint(button, false);
        toast('之前并未关注该用户', 'info');
      } else {
        toast(err.message || '关注操作失败', 'error');
      }
    } finally {
      button.disabled = false;
    }
  }

  function paint(button, followed) {
    button.dataset.followed = followed ? '1' : '0';
    button.textContent = followed ? '已关注' : '关注';
    button.classList.toggle('btn-primary', !followed);
    button.classList.toggle('btn-outline', followed);
  }

  return {
    setTab(nextTab) {
      tab = nextTab === 'fans' ? 'fans' : 'following';
      page = 1;
      return load();
    },
    reload: load,
    getTab: () => tab,
  };
}
