/** 发现页：搜索入口 + 热门笔记推荐流 + 快捷入口 */

import { api } from '../api.js';
import { HOT_KEYWORDS } from '../constants.js';
import { icons } from '../icons.js';
import { navigate } from '../router.js';
import {
  bindPagination,
  emptyState,
  errorState,
  noteCard,
  pagination,
  skeletonGrid,
  toast,
} from '../ui.js';
import { escapeHtml, isAbortError, parseNoteId } from '../utils.js';

export default {
  async mount(root, ctx) {
    const keywordChips = HOT_KEYWORDS.map(
      (keyword) =>
        `<a class="chip" href="#/search?q=${encodeURIComponent(keyword)}">${escapeHtml(
          keyword
        )}</a>`
    ).join('');

    root.innerHTML = `<div class="explore-page">
      <section class="hero">
        <div class="hero-content">
          <span class="eyebrow">${icons.sparkles()} 发现你的热爱</span>
          <h1>搜一搜，<br>遇见<em>有趣的笔记</em></h1>
          <p class="hero-sub">找到你感兴趣的笔记和创作者。</p>
          <form class="hero-search" id="hero-search" role="search">
            <label class="sr-only" for="hero-keyword">搜索关键词</label>
            <input id="hero-keyword" name="q" type="search" placeholder="搜索笔记、用户，例如：咖啡、旅行" autocomplete="off">
            <button class="btn btn-primary" type="submit">${icons.search()} 搜索</button>
          </form>
          <div class="hero-chips">
            <span>热门：</span>
            ${keywordChips}
          </div>
        </div>
        <div class="hero-visual" aria-hidden="true">
          <span class="hv-aura"></span>

          <div class="hv-waves">
            <span></span><span></span><span></span><span></span>
          </div>

          <div class="hv-search">
            <span class="hv-search-glow"></span>
            <span class="hv-search-icon">${icons.search()}</span>
          </div>

          <div class="hv-cards">
            <span class="hv-card hv-card--1"></span>
            <span class="hv-card hv-card--2"></span>
            <span class="hv-card hv-card--3"></span>
            <span class="hv-card hv-card--4"></span>
          </div>

          <div class="hv-bubbles">
            <span class="hv-bubble" style="--x: 2%; --y: 4%; --delay: 0s">手绘</span>
            <span class="hv-bubble" style="--x: 56%; --y: -2%; --delay: 0.9s">摄影</span>
            <span class="hv-bubble hv-bubble--sm" style="--x: 86%; --y: 20%; --delay: 1.4s">穿搭</span>
            <span class="hv-bubble hv-bubble--sm" style="--x: 90%; --y: 52%; --delay: 0.4s">美食</span>
            <span class="hv-bubble" style="--x: 50%; --y: 86%; --delay: 1.9s">生活记录</span>
          </div>

          <span class="hv-spark" style="--x: 14%; --y: 26%; --dur: 3.4s; --delay: 0s"></span>
          <span class="hv-spark" style="--x: 44%; --y: 8%; --dur: 4.6s; --delay: 0.7s"></span>
          <span class="hv-spark" style="--x: 78%; --y: 72%; --dur: 3.9s; --delay: 1.2s"></span>
        </div>
      </section>

      <div class="explore-columns">
        <section class="section">
          <div class="section-head">
            <h2 class="section-title">${icons.heart()} 热门笔记</h2>
          </div>
          <div id="hot-notes"></div>
        </section>

        <aside class="explore-aside">
          <div class="quick-card">
            <h3>${icons.link()} 按链接打开笔记</h3>
            <p>粘贴笔记链接，直接打开对应的笔记。</p>
            <form class="direct-id" id="direct-id-form">
              <label class="sr-only" for="direct-id-input">笔记链接</label>
              <input class="input" id="direct-id-input" type="text" placeholder="粘贴笔记链接" autocomplete="off">
              <button class="btn btn-soft" type="submit">打开</button>
            </form>
          </div>

          <div class="quick-card">
            <h3>${icons.zap()} 快捷入口</h3>
            <div class="quick-links">
              <a class="quick-link" href="#/search?tab=users">${icons.users()} 搜索用户 <span class="arrow">${icons.chevronRight()}</span></a>
              <a class="quick-link" href="#/publish">${icons.plus()} 发布图文 / 视频 <span class="arrow">${icons.chevronRight()}</span></a>
              <a class="quick-link" href="#/me">${icons.user()} 我的主页 <span class="arrow">${icons.chevronRight()}</span></a>
            </div>
          </div>
        </aside>
      </div>
    </div>`;

    const heroForm = root.querySelector('#hero-search');
    heroForm?.addEventListener('submit', (event) => {
      event.preventDefault();
      const keyword = heroForm.querySelector('input')?.value.trim();
      if (!keyword) {
        toast('请输入搜索关键词', 'warning');
        heroForm.querySelector('input')?.focus();
        return;
      }
      navigate('/search', { q: keyword });
    });

    const directForm = root.querySelector('#direct-id-form');
    directForm?.addEventListener('submit', (event) => {
      event.preventDefault();
      const input = directForm.querySelector('input');
      const id = parseNoteId(input?.value || '');
      if (!id) {
        toast('请输入有效的笔记链接', 'warning');
        input?.focus();
        return;
      }
      navigate(`/note/${id}`);
    });

    /* ------------------------------- 热门笔记 ------------------------------ */

    const hotEl = root.querySelector('#hot-notes');
    let hotPage = 1;

    const loadHot = async (pageNo = 1) => {
      hotPage = pageNo;
      hotEl.innerHTML = skeletonGrid(8);

      try {
        const res = await api.hotNotes(pageNo, { signal: ctx.signal });
        if (ctx.signal.aborted) return;

        const list = Array.isArray(res?.data) ? res.data : [];
        const total = Number(res?.totalCount) || 0;
        const totalPage = Number(res?.totalPage) || 1;

        if (!list.length) {
          hotEl.innerHTML = emptyState({
            icon: 'compass',
            title: '还没有热门笔记',
            desc: '搜索一个关键词，或者发布第一篇笔记吧。',
            actions: [
              { label: '去搜索', href: '#/search', variant: 'btn-primary' },
              { label: '发布笔记', href: '#/publish' },
            ],
          });
          return;
        }

        hotEl.innerHTML = `<div class="note-grid is-compact">${list
          .map((note) => noteCard(note))
          .join('')}</div>
        ${pagination({ pageNo: Number(res?.pageNo) || pageNo, totalPage, totalCount: total, id: 'hot-pager' })}`;

        bindPagination(hotEl.querySelector('#hot-pager'), (nextPage) => {
          loadHot(nextPage);
          scrollToSection();
        });
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;
        hotEl.innerHTML = errorState({
          error: err,
          title: '热门笔记加载失败',
          retryId: 'hot-retry',
        });
        hotEl.querySelector('#hot-retry')?.addEventListener('click', () => loadHot(hotPage));
      }
    };

    const scrollToSection = () => {
      hotEl.closest('.section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };

    loadHot(1);
  },
};
