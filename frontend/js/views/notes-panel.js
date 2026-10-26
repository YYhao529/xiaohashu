/**
 * 发布 / 收藏 / 点赞 笔记列表面板（个人主页与他人主页共用）
 */

import { api } from '../api.js';
import {
  bindPagination,
  emptyState,
  errorState,
  noteCard,
  pagination,
  skeletonGrid,
} from '../ui.js';
import { isAbortError } from '../utils.js';

const TAB_META = {
  publish: {
    label: '笔记',
    empty: {
      icon: 'image',
      title: '还没有发布笔记',
      desc: '发布第一篇笔记，记录你的生活吧。',
      actions: [{ label: '去发布', href: '#/publish', variant: 'btn-primary' }],
    },
  },
  collect: {
    label: '收藏',
    empty: {
      icon: 'star',
      title: '还没有收藏笔记',
      desc: '在笔记详情页点击收藏，之后就能在这里找到它们。',
      actions: [{ label: '去发现', href: '#/explore', variant: 'btn-primary' }],
    },
  },
  like: {
    label: '赞过',
    empty: {
      icon: 'heart',
      title: '还没有点赞笔记',
      desc: '在笔记详情页点赞，之后就能在这里找到它们。',
      actions: [{ label: '去发现', href: '#/explore', variant: 'btn-primary' }],
    },
  },
};

const FETCHERS = {
  publish: (userId, page, options) => api.userPublishedNotes(userId, page, options),
  collect: (userId, page, options) => api.userCollectedNotes(userId, page, options),
  like: (userId, page, options) => api.userLikedNotes(userId, page, options),
};

export function createNotesPanel({ container, userId, signal, onTotal }) {
  let tab = 'publish';
  let page = 1;
  let seq = 0;

  async function load() {
    const current = ++seq;
    container.innerHTML = skeletonGrid(6);

    try {
      const res = await FETCHERS[tab](userId, page, { signal });

      if (current !== seq || signal.aborted) return;

      onTotal?.(tab, Number(res?.totalCount) || 0);
      render(res);
    } catch (err) {
      if (isAbortError(err) || signal.aborted) return;
      container.innerHTML = errorState({
        error: err,
        title: `${TAB_META[tab].label}列表加载失败`,
        retryId: 'notes-retry',
      });
      container.querySelector('#notes-retry')?.addEventListener('click', load);
    }
  }

  function render(res) {
    const list = Array.isArray(res?.data) ? res.data : [];
    const total = Number(res?.totalCount) || 0;
    const totalPage = Number(res?.totalPage) || 1;
    const pageNo = Number(res?.pageNo) || page;

    if (!list.length) {
      container.innerHTML = emptyState(TAB_META[tab].empty);
      return;
    }

    container.innerHTML = `<div class="note-grid">${list
      .map((note) => noteCard(note))
      .join('')}</div>
    ${pagination({ pageNo, totalPage, totalCount: total, id: 'notes-pager' })}`;

    bindPagination(container.querySelector('#notes-pager'), (nextPage) => {
      page = nextPage;
      load();
    });
  }

  /**
   * 预加载各 tab 的笔记数量（只取 totalCount，不渲染）
   *
   * @param exceptTab 已经由当前 tab 拉取过的类型，避免重复请求
   */
  async function preloadTotals(exceptTab) {
    const kinds = Object.keys(FETCHERS).filter((kind) => kind !== exceptTab);

    await Promise.all(
      kinds.map(async (kind) => {
        try {
          const res = await FETCHERS[kind](userId, 1, { signal });
          if (signal.aborted) return;
          onTotal?.(kind, Number(res?.totalCount) || 0);
        } catch (err) {
          /* 预加载失败不影响当前 tab */
        }
      })
    );
  }

  return {
    setTab(nextTab) {
      tab = FETCHERS[nextTab] ? nextTab : 'publish';
      page = 1;
      return load();
    },
    preloadTotals,
    reload: load,
    getTab: () => tab,
  };
}
