/** 创作页：发布 / 编辑笔记（图文或视频） */

import { api } from '../api.js';
import { icons } from '../icons.js';
import { resolveMe } from '../session.js';
import { auth } from '../store.js';
import { errorState, openDialog, setLoading, toast } from '../ui.js';
import { back, navigate } from '../router.js';
import { escapeHtml, isAbortError, renderRichText, safeUrl } from '../utils.js';

const MAX_IMAGES = 8;
const MAX_IMAGE_SIZE = 10 * 1024 * 1024;

export default {
  async mount(root, ctx) {
    const isEdit = Boolean(ctx.params.id);
    const noteId = isEdit ? String(ctx.params.id) : '';

    if (!auth.isLoggedIn) {
      navigate('/login', { redirect: isEdit ? `/note/${noteId}/edit` : '/publish' });
      return;
    }

    if (isEdit && !/^\d+$/.test(noteId)) {
      root.innerHTML = `<div class="page">${errorState({
        error: { message: '笔记 ID 不合法' },
        title: '无法编辑',
        showCode: false,
      })}</div>`;
      return;
    }

    root.innerHTML = `<div class="editor-page">
      <div class="detail-topbar">
        <button class="back-link" type="button" id="editor-back">${icons.arrowLeft()} 返回</button>
        <span class="badge badge-brand">${isEdit ? '编辑笔记' : '发布笔记'}</span>
      </div>
      <div class="page-head">
        <div>
          <h1 class="page-title">${isEdit ? '编辑你的笔记' : '分享你的生活灵感'}</h1>
          <p class="page-sub">${
            isEdit
              ? '修改会立即生效。'
              : '分享你的生活灵感，支持图文与视频两种形式。'
          }</p>
        </div>
      </div>
      <div class="page"><div class="skeleton-card" style="padding:28px;gap:18px">
        <div class="skeleton skeleton-line" style="width:30%"></div>
        <div class="skeleton" style="height:170px;border-radius:16px"></div>
        <div class="skeleton skeleton-line" style="width:65%"></div>
        <div class="skeleton skeleton-line" style="width:45%"></div>
      </div></div>
    </div>`;

    let note = null;
    let state = {
      type: 0,
      images: [],
      videoUri: '',
      uploading: 0,
    };

    root.querySelector('#editor-back')?.addEventListener('click', () => {
      if (isEdit) navigate(`/note/${noteId}`);
      else back('/explore');
    });

    if (isEdit) {
      try {
        note = await api.noteDetail(noteId, { signal: ctx.signal });

        if (!auth.me) {
          try {
            await resolveMe({ signal: ctx.signal });
          } catch (err) {
            /* 忽略：下面会给出无权限提示 */
          }
        }

        if (!auth.me?.id || String(auth.me.id) !== String(note.creatorId)) {
          root.innerHTML = `<div class="page">${errorState({
            error: {
              message: '只有笔记作者本人可以编辑这篇笔记。',
              code: 'NOTE-20006',
            },
            title: '没有编辑权限',
            retryId: 'back-to-note',
            showCode: true,
          })}<div class="row" style="justify-content:center"><a class="btn btn-outline" href="#/note/${encodeURIComponent(
            noteId
          )}">返回笔记详情</a></div></div>`;
          root.querySelector('#back-to-note')?.addEventListener('click', () => {
            navigate(`/note/${noteId}`);
          });
          return;
        }

        state.type = Number(note.type) === 1 ? 1 : 0;
        state.images = (Array.isArray(note.imgUris) ? note.imgUris : [])
          .map((uri) => safeUrl(uri))
          .filter(Boolean);
        state.videoUri = note.videoUri ? safeUrl(note.videoUri) : '';
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;
        root.innerHTML = `<div class="page">${errorState({
          error: err,
          title: '笔记加载失败',
          retryId: 'editor-retry',
        })}</div>`;
        root.querySelector('#editor-retry')?.addEventListener('click', () => {
          navigate(isEdit ? `/note/${noteId}/edit` : '/publish', {}, { replace: true });
        });
        return;
      }
    }

    renderEditor();

    function renderEditor() {
      root.innerHTML = `<div class="editor-page">
        <div class="detail-topbar">
          <button class="back-link" type="button" id="editor-back">${icons.arrowLeft()} 返回</button>
          <span class="badge badge-brand">${isEdit ? '编辑笔记' : '发布笔记'}</span>
        </div>

        <div class="page-head">
          <div>
            <h1 class="page-title">${isEdit ? '编辑你的笔记' : '分享你的生活灵感'}</h1>
            <p class="page-sub">${
              isEdit
                ? '修改会立即生效。'
                : '分享你的生活灵感，支持图文与视频两种形式。'
            }</p>
          </div>
        </div>

        <div class="editor-grid">
          <form class="editor-main" id="editor-form" novalidate>
            <div class="field">
              <span class="field-label">笔记类型</span>
              <div class="type-switch">
                <button class="type-option" type="button" data-type="0">
                  <span class="type-icon">${icons.image()}</span>
                  <span class="type-text"><strong>图文笔记</strong><small>1 - 8 张图片</small></span>
                </button>
                <button class="type-option" type="button" data-type="1">
                  <span class="type-icon">${icons.video()}</span>
                  <span class="type-text"><strong>视频笔记</strong><small>单个视频链接</small></span>
                </button>
              </div>
            </div>

            <div class="field" data-panel="image">
              <div class="field-label">
                图片
                <span class="optional">最多 ${MAX_IMAGES} 张，第 1 张作为封面</span>
              </div>
              <div class="upload-grid" id="upload-grid"></div>
              <input id="image-input" type="file" accept="image/*" multiple hidden>
              <div class="image-url-row">
                <input class="input" id="image-url" type="url" placeholder="或粘贴图片链接 https://…" autocomplete="off">
                <button class="btn btn-outline" id="add-image-url" type="button">添加链接</button>
              </div>
              <p class="field-hint">支持 JPG、PNG、WebP 等格式，单张不超过 10MB；也可以使用外链图片。</p>
            </div>

            <div class="field" data-panel="video" hidden>
              <div class="field-label">
                视频
                <span class="optional">支持 MP4 等常见格式</span>
              </div>
              <div class="image-url-row">
                <input class="input" id="video-url" type="url" placeholder="视频链接 https://…" autocomplete="off">
                <button class="btn btn-outline" id="video-upload" type="button">${icons.upload()} 上传</button>
              </div>
              <input id="video-input" type="file" accept="video/*" hidden>
              <p class="field-hint">支持 MP4 等常见视频格式。</p>
            </div>

            <div class="field">
              <div class="field-label">
                <label for="note-title">标题</label>
                <span class="optional"><span id="title-count">0</span> / 30</span>
              </div>
              <div class="input-emoji-wrap">
                <input class="input" id="note-title" type="text" maxlength="30"
                  placeholder="给笔记起一个吸引人的标题" autocomplete="off"
                  value="${escapeHtml(note?.title || '')}">
                <button class="input-emoji-btn" type="button" id="title-emoji-toggle"
                  title="插入表情" aria-label="插入表情" aria-haspopup="true" aria-expanded="false"
                  data-emoji-toggle>${icons.smile()}</button>
                <div class="emoji-pop emoji-pop-right" id="title-emoji-pop" hidden>
                  <div class="emoji-pop-title">插入到标题</div>
                  <div class="emoji-groups" id="title-emoji-groups"></div>
                </div>
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="note-content">正文</label>
              <div class="rich-toolbar" id="rich-toolbar" role="toolbar" aria-label="正文格式">
                <div class="rich-group">
                  <button class="rich-btn" type="button" data-format="bold" title="加粗（Ctrl+B）" aria-label="加粗">
                    <span class="rich-glyph is-bold">B</span>
                  </button>
                  <button class="rich-btn" type="button" data-format="italic" title="斜体（Ctrl+I）" aria-label="斜体">
                    <span class="rich-glyph is-italic">I</span>
                  </button>
                  <button class="rich-btn" type="button" data-format="underline" title="下划线（Ctrl+U）" aria-label="下划线">
                    <span class="rich-glyph is-underline">U</span>
                  </button>
                  <button class="rich-btn" type="button" data-format="strike" title="删除线" aria-label="删除线">
                    <span class="rich-glyph is-strike">S</span>
                  </button>
                </div>
                <span class="rich-sep" aria-hidden="true"></span>
                <div class="rich-group">
                  <button class="rich-btn" type="button" data-format="ul" title="无序列表" aria-label="无序列表">${icons.listUl()}</button>
                  <button class="rich-btn" type="button" data-format="ol" title="有序列表" aria-label="有序列表">${icons.listOl()}</button>
                  <button class="rich-btn" type="button" id="emoji-toggle" title="插入表情" aria-label="插入表情"
                    aria-haspopup="true" aria-expanded="false" data-emoji-toggle>${icons.smile()}</button>
                </div>
                <span class="grow"></span>
                <button class="rich-btn rich-preview-btn" type="button" id="content-preview-toggle" aria-pressed="false">
                  ${icons.eye()} <span>预览</span>
                </button>
                <div class="emoji-pop" id="emoji-pop" hidden>
                  <div class="emoji-pop-title">选择表情</div>
                  <div class="emoji-groups" id="emoji-groups"></div>
                </div>
              </div>
              <textarea class="textarea" id="note-content" rows="8"
                placeholder="分享你的体验、攻略或心情…">${escapeHtml(note?.content || '')}</textarea>
              <div class="rich-preview" id="content-preview" hidden></div>
              <p class="field-hint">支持 emoji 与格式：**加粗**、*斜体*、__下划线__、~~删除线~~，行首用「- 」或「1. 」做列表。</p>
            </div>

            <div class="field">
              <div class="field-label">
                <label for="note-topic">话题名称</label>
                <span class="optional">可选</span>
              </div>
              <input class="input" id="note-topic" type="text"
                placeholder="例如：美食" autocomplete="off" maxlength="30"
                value="${note?.topicName ? escapeHtml(String(note.topicName)) : ''}">
              <p class="field-hint">可选，直接填写话题名称即可；话题不存在时会自动创建。</p>
            </div>

            <div class="editor-actions">
              <span class="grow">${icons.shield()} 请确认内容符合社区规范</span>
              <a class="btn btn-ghost" href="${
                isEdit ? `#/note/${encodeURIComponent(noteId)}` : '#/explore'
              }">取消</a>
              <button class="btn btn-primary" type="submit" id="editor-submit">
                ${isEdit ? '保存修改' : '发布笔记'}
              </button>
            </div>
          </form>

          <aside class="editor-side">
            <div class="preview-card">
              <h3>${icons.eye()} 实时预览</h3>
              <div class="preview-note">
                <div class="preview-cover" id="preview-cover"></div>
                <div class="preview-body">
                  <div class="preview-title" id="preview-title"></div>
                  <div class="preview-meta" id="preview-meta"></div>
                </div>
              </div>
            </div>

            <div class="editor-hints">
              <div><strong>发布说明</strong></div>
              <div>· 图文笔记至少需要 1 张图片，最多 ${MAX_IMAGES} 张。</div>
              <div>· 视频笔记需要提供视频链接或上传视频文件。</div>
              <div>· 发布成功后，可在搜索中通过标题找到笔记。</div>
            </div>
          </aside>
        </div>
      </div>`;

      const form = root.querySelector('#editor-form');
      const uploadGrid = root.querySelector('#upload-grid');
      const imageInput = root.querySelector('#image-input');
      const imageUrlInput = root.querySelector('#image-url');
      const addImageUrlBtn = root.querySelector('#add-image-url');
      const videoUrlInput = root.querySelector('#video-url');
      const videoInput = root.querySelector('#video-input');
      const videoUploadBtn = root.querySelector('#video-upload');
      const titleInput = root.querySelector('#note-title');
      const titleCount = root.querySelector('#title-count');
      const contentInput = root.querySelector('#note-content');
      const topicInput = root.querySelector('#note-topic');
      const submitBtn = root.querySelector('#editor-submit');
      const typeButtons = [...root.querySelectorAll('[data-type]')];
      const imagePanel = root.querySelector('[data-panel="image"]');
      const videoPanel = root.querySelector('[data-panel="video"]');

      /* --------------------------- 正文富文本工具栏 --------------------------- */

      const previewEl = root.querySelector('#content-preview');
      const previewToggle = root.querySelector('#content-preview-toggle');

      const syncPreview = () => {
        if (!previewEl || previewEl.hidden) return;
        previewEl.innerHTML =
          renderRichText(contentInput.value) || '<span class="rich-preview-empty">还没有正文</span>';
      };

      previewToggle?.addEventListener('click', () => {
        const show = previewEl.hidden;
        previewEl.hidden = !show;
        previewToggle.setAttribute('aria-pressed', String(show));
        previewToggle.classList.toggle('is-active', show);
        if (show) syncPreview();
      });

      /* ------------------------------ 表情选择器 ------------------------------ */

      const EMOJI_GROUPS = [
        {
          label: '常用',
          emojis: ['😀', '😄', '😁', '😂', '🤣', '😊', '😍', '🥰', '😘', '😎', '🤔', '😅', '😭', '😡', '🥳', '😴'],
        },
        {
          label: '手势',
          emojis: ['👍', '👎', '👏', '🙌', '🤝', '✌️', '👌', '🙏', '💪', '👋', '🤙', '👀'],
        },
        {
          label: '美食',
          emojis: ['🍜', '🍲', '🍣', '🍰', '☕', '🍺', '🍎', '🍓', '🌶️', '🍕', '🍔', '🧋'],
        },
        {
          label: '生活',
          emojis: ['✨', '🔥', '💯', '❤️', '💔', '🎉', '🎁', '🌈', '☀️', '🌙', '⭐', '📷', '🎵', '✈️'],
        },
        {
          label: '自然',
          emojis: ['🌊', '🏔️', '🌸', '🌻', '🍀', '🐱', '🐶', '🐼', '🐰', '🌵', '🍂', '🌿'],
        },
      ];

      const emojiToggle = root.querySelector('#emoji-toggle');
      const emojiPop = root.querySelector('#emoji-pop');
      const emojiGroups = root.querySelector('#emoji-groups');
      const titleEmojiToggle = root.querySelector('#title-emoji-toggle');
      const titleEmojiPop = root.querySelector('#title-emoji-pop');
      const titleEmojiGroups = root.querySelector('#title-emoji-groups');

      // 正文和标题共用同一份表情数据
      const renderEmojiGroups = (container) => {
        if (!container) return;
        container.innerHTML = EMOJI_GROUPS.map(
          (group) => `<div class="emoji-group">
            <span class="emoji-group-label">${escapeHtml(group.label)}</span>
            <div class="emoji-grid">
              ${group.emojis
                .map(
                  (emoji) =>
                    `<button class="emoji-item" type="button" data-emoji="${emoji}" aria-label="插入 ${emoji}">${emoji}</button>`
                )
                .join('')}
            </div>
          </div>`
        ).join('');
      };

      renderEmojiGroups(emojiGroups);
      renderEmojiGroups(titleEmojiGroups);

      const insertAtCursor = (text) => {
        const start = contentInput.selectionStart ?? contentInput.value.length;
        const end = contentInput.selectionEnd ?? contentInput.value.length;
        const value = contentInput.value;

        contentInput.value = `${value.slice(0, start)}${text}${value.slice(end)}`;
        contentInput.focus();
        const pos = start + text.length;
        contentInput.setSelectionRange(pos, pos);
        syncPreview();
      };

      // 插入到标题：受 30 字上限约束，插入后触发 input 事件同步计数与预览
      const insertIntoTitle = (text) => {
        const start = titleInput.selectionStart ?? titleInput.value.length;
        const end = titleInput.selectionEnd ?? titleInput.value.length;
        const value = titleInput.value;
        const next = `${value.slice(0, start)}${text}${value.slice(end)}`;

        if (next.length > 30) {
          toast('标题最多 30 个字符', 'warning');
          return;
        }

        titleInput.value = next;
        titleInput.focus();
        const pos = start + text.length;
        titleInput.setSelectionRange(pos, pos);
        titleInput.dispatchEvent(new Event('input', { bubbles: true }));
      };

      emojiToggle?.addEventListener('click', (event) => {
        event.preventDefault();
        // 打开一个面板前先关掉另一个
        if (titleEmojiPop && !titleEmojiPop.hidden) {
          titleEmojiPop.hidden = true;
          titleEmojiToggle?.setAttribute('aria-expanded', 'false');
          titleEmojiToggle?.classList.remove('is-active');
        }
        const open = emojiPop.hidden;
        emojiPop.hidden = !open;
        emojiToggle.setAttribute('aria-expanded', String(open));
        emojiToggle.classList.toggle('is-active', open);
      });

      // 标题的表情面板
      titleEmojiToggle?.addEventListener('click', (event) => {
        event.preventDefault();
        if (emojiPop && !emojiPop.hidden) {
          emojiPop.hidden = true;
          emojiToggle?.setAttribute('aria-expanded', 'false');
          emojiToggle?.classList.remove('is-active');
        }
        const open = titleEmojiPop.hidden;
        titleEmojiPop.hidden = !open;
        titleEmojiToggle.setAttribute('aria-expanded', String(open));
        titleEmojiToggle.classList.toggle('is-active', open);
      });

      // 按下时阻止默认行为，保住 textarea 的光标位置
      emojiGroups?.addEventListener('mousedown', (event) => {
        if (event.target.closest('[data-emoji]')) event.preventDefault();
      });

      emojiGroups?.addEventListener('click', (event) => {
        const item = event.target.closest('[data-emoji]');
        if (!item) return;
        insertAtCursor(item.dataset.emoji);
      });

      // 标题面板：同样保住 input 的光标位置
      titleEmojiGroups?.addEventListener('mousedown', (event) => {
        if (event.target.closest('[data-emoji]')) event.preventDefault();
      });

      titleEmojiGroups?.addEventListener('click', (event) => {
        const item = event.target.closest('[data-emoji]');
        if (!item) return;
        insertIntoTitle(item.dataset.emoji);
      });

      // 点击别处 / Esc 关闭所有表情面板（只注册一次，各页面复用）
      if (!window.__xhsEmojiOutsideBound) {
        window.__xhsEmojiOutsideBound = true;

        const closeAllEmojiPops = () => {
          document.querySelectorAll('.emoji-pop').forEach((pop) => {
            pop.hidden = true;
          });
          document.querySelectorAll('[data-emoji-toggle]').forEach((toggle) => {
            toggle.setAttribute('aria-expanded', 'false');
            toggle.classList.remove('is-active');
          });
        };

        document.addEventListener('click', (event) => {
          if (
            event.target.closest?.('.emoji-pop') ||
            event.target.closest?.('[data-emoji-toggle]')
          ) {
            return;
          }
          closeAllEmojiPops();
        });

        document.addEventListener('keydown', (event) => {
          if (event.key !== 'Escape') return;
          closeAllEmojiPops();
        });
      }

      const wrapSelection = (before, after, placeholder) => {
        const start = contentInput.selectionStart;
        const end = contentInput.selectionEnd;
        const value = contentInput.value;
        const selected = value.slice(start, end) || placeholder;

        contentInput.value = `${value.slice(0, start)}${before}${selected}${after}${value.slice(end)}`;
        contentInput.focus();
        contentInput.setSelectionRange(start + before.length, start + before.length + selected.length);
        syncPreview();
      };

      const prefixSelectedLines = (ordered) => {
        const start = contentInput.selectionStart;
        const end = contentInput.selectionEnd;
        const value = contentInput.value;
        const lineStart = value.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        let lineEnd = value.indexOf('\n', end);
        if (lineEnd === -1) lineEnd = value.length;

        const replaced = value
          .slice(lineStart, lineEnd)
          .split('\n')
          .map((line, index) => (ordered ? `${index + 1}. ` : '- ') + line)
          .join('\n');

        contentInput.value = `${value.slice(0, lineStart)}${replaced}${value.slice(lineEnd)}`;
        contentInput.focus();
        contentInput.setSelectionRange(lineStart, lineStart + replaced.length);
        syncPreview();
      };

      root.querySelector('#rich-toolbar')?.addEventListener('click', (event) => {
        const button = event.target.closest('[data-format]');
        if (!button) return;
        event.preventDefault();

        switch (button.dataset.format) {
          case 'bold':
            wrapSelection('**', '**', '粗体文字');
            break;
          case 'italic':
            wrapSelection('*', '*', '斜体文字');
            break;
          case 'underline':
            wrapSelection('__', '__', '下划线文字');
            break;
          case 'strike':
            wrapSelection('~~', '~~', '删除线文字');
            break;
          case 'ul':
            prefixSelectedLines(false);
            break;
          case 'ol':
            prefixSelectedLines(true);
            break;
          default:
            break;
        }
      });

      contentInput?.addEventListener('input', syncPreview);
      contentInput?.addEventListener('keydown', (event) => {
        if (!(event.ctrlKey || event.metaKey)) return;

        const key = event.key.toLowerCase();
        if (key === 'b') {
          event.preventDefault();
          wrapSelection('**', '**', '粗体文字');
        } else if (key === 'i') {
          event.preventDefault();
          wrapSelection('*', '*', '斜体文字');
        } else if (key === 'u') {
          event.preventDefault();
          wrapSelection('__', '__', '下划线文字');
        }
      });

      root.querySelector('#editor-back')?.addEventListener('click', () => {
        if (isEdit) navigate(`/note/${noteId}`);
        else back('/explore');
      });

      const setType = (type) => {
        state.type = type === 1 ? 1 : 0;
        typeButtons.forEach((button) => {
          button.classList.toggle(
            'is-active',
            Number(button.dataset.type) === state.type
          );
        });
        imagePanel.hidden = state.type !== 0;
        videoPanel.hidden = state.type !== 1;
        renderPreview();
      };

      const renderImages = () => {
        uploadGrid.innerHTML = `${
          state.images
            .map(
              (src, index) => `<div class="upload-item">
                <img src="${escapeHtml(src)}" alt="第 ${index + 1} 张图片" loading="lazy" data-fallback="1">
                <span class="upload-index">${index + 1}</span>
                <button class="upload-remove" type="button" data-remove="${index}" aria-label="移除第 ${
                index + 1
              } 张图片">${icons.close()}</button>
              </div>`
            )
            .join('') || ''
        }
        ${
          state.uploading > 0
            ? `<div class="upload-add is-loading"><span class="spinner"></span><span>上传中 ${state.uploading}</span></div>`
            : ''
        }
        ${
          state.images.length < MAX_IMAGES
            ? `<button class="upload-add" type="button" data-add-image>
                ${icons.plus()}<span>添加图片</span>
              </button>`
            : ''
        }`;
      };

      const renderPreview = () => {
        const coverEl = root.querySelector('#preview-cover');
        const titleEl = root.querySelector('#preview-title');
        const metaEl = root.querySelector('#preview-meta');
        if (!coverEl || !titleEl || !metaEl) return;

        if (state.type === 0 && state.images[0]) {
          coverEl.innerHTML = `<img src="${escapeHtml(
            state.images[0]
          )}" alt="" data-fallback="1">`;
        } else {
          coverEl.innerHTML =
            state.type === 1
              ? `${icons.video()}<span>视频笔记</span>`
              : `${icons.image()}<span>还没有图片</span>`;
        }

        titleEl.textContent =
          titleInput.value.trim() || (state.type === 1 ? '视频笔记标题预览' : '笔记标题预览');
        metaEl.textContent =
          state.type === 1
            ? '视频笔记'
            : `图文笔记 · ${state.images.length} 张图片${
                state.images.length ? ' · 第 1 张为封面' : ''
              }`;
      };

      const addFiles = async (files) => {
        const list = [...files].filter((file) => file.type.startsWith('image/'));
        const remaining = MAX_IMAGES - state.images.length;
        if (!remaining) {
          toast(`最多只能上传 ${MAX_IMAGES} 张图片`, 'warning');
          return;
        }
        if (!list.length) {
          toast('请选择图片文件', 'warning');
          return;
        }

        const accepted = list.slice(0, remaining);
        if (list.length > remaining) {
          toast(`最多只能上传 ${MAX_IMAGES} 张图片，多余的已忽略`, 'warning');
        }

        for (const file of accepted) {
          if (file.size > MAX_IMAGE_SIZE) {
            toast(`「${file.name}」超过 10MB，已跳过`, 'warning');
            continue;
          }

          state.uploading += 1;
          renderImages();

          try {
            const url = await api.uploadFile(file, { signal: ctx.signal });
            const safe = safeUrl(url);
            if (!safe) throw new Error('上传失败，请重试');
            state.images.push(safe);
          } catch (err) {
            if (isAbortError(err) || ctx.signal.aborted) return;
            toast(err.message || '图片上传失败', 'error');
          } finally {
            state.uploading = Math.max(0, state.uploading - 1);
            renderImages();
            renderPreview();
          }
        }

        if (imageInput) imageInput.value = '';
      };

      const addImageUrl = () => {
        const raw = imageUrlInput.value.trim();
        const url = safeUrl(raw);
        if (!url) {
          toast('请输入有效的 http(s) 图片链接', 'warning');
          imageUrlInput.focus();
          return;
        }
        if (state.images.length >= MAX_IMAGES) {
          toast(`最多只能上传 ${MAX_IMAGES} 张图片`, 'warning');
          return;
        }
        state.images.push(url);
        imageUrlInput.value = '';
        renderImages();
        renderPreview();
      };

      typeButtons.forEach((button) => {
        button.addEventListener('click', () => setType(Number(button.dataset.type)));
      });

      uploadGrid.addEventListener('click', (event) => {
        const addButton = event.target.closest('[data-add-image]');
        if (addButton) {
          imageInput?.click();
          return;
        }

        const removeButton = event.target.closest('[data-remove]');
        if (removeButton) {
          const index = Number(removeButton.dataset.remove);
          if (Number.isInteger(index)) {
            state.images.splice(index, 1);
            renderImages();
            renderPreview();
          }
        }
      });

      imageInput?.addEventListener('change', () => {
        if (imageInput.files?.length) addFiles(imageInput.files);
      });

      addImageUrlBtn?.addEventListener('click', addImageUrl);
      imageUrlInput?.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          addImageUrl();
        }
      });

      videoUrlInput?.addEventListener('input', () => {
        state.videoUri = videoUrlInput.value.trim();
      });

      videoUploadBtn?.addEventListener('click', () => videoInput?.click());
      videoInput?.addEventListener('change', async () => {
        const file = videoInput.files?.[0];
        if (!file) return;

        videoUploadBtn.disabled = true;
        videoUploadBtn.classList.add('is-loading');
        try {
          const url = await api.uploadFile(file, { signal: ctx.signal });
          const safe = safeUrl(url);
          if (!safe) throw new Error('上传失败，请重试');
          state.videoUri = safe;
          videoUrlInput.value = safe;
          toast('视频上传成功', 'success');
        } catch (err) {
          if (!isAbortError(err) && !ctx.signal.aborted) {
            toast(err.message || '视频上传失败', 'error');
          }
        } finally {
          videoUploadBtn.disabled = false;
          videoUploadBtn.classList.remove('is-loading');
          videoInput.value = '';
        }
      });

      titleInput?.addEventListener('input', () => {
        titleCount.textContent = String(titleInput.value.length);
        renderPreview();
      });
      contentInput?.addEventListener('input', renderPreview);

      form?.addEventListener('submit', async (event) => {
        event.preventDefault();

        if (state.uploading > 0) {
          toast('图片还在上传中，请稍候再发布', 'warning');
          return;
        }

        const title = titleInput.value.trim();
        if (!title) {
          toast('请填写笔记标题', 'warning');
          titleInput.focus();
          return;
        }

        if (state.type === 0) {
          if (!state.images.length) {
            toast('图文笔记至少需要 1 张图片', 'warning');
            return;
          }
          if (state.images.length > MAX_IMAGES) {
            toast(`图片不能超过 ${MAX_IMAGES} 张`, 'warning');
            return;
          }
        } else if (!safeUrl(state.videoUri)) {
          toast('视频笔记需要填写或上传视频', 'warning');
          videoUrlInput?.focus();
          return;
        }

        const topicRaw = topicInput.value.trim();
        if (topicRaw.length > 30) {
          toast('话题名称不能超过 30 个字', 'warning');
          topicInput.focus();
          return;
        }

        const payload = {
          type: state.type,
          title,
          content: contentInput.value.trim(),
        };
        if (state.type === 0) payload.imgUris = state.images;
        else payload.videoUri = state.videoUri;
        if (topicRaw) payload.topicName = topicRaw;

        setLoading(submitBtn, true);

        try {
          if (isEdit) {
            payload.id = noteId;
            await api.updateNote(payload, { signal: ctx.signal });
            toast('笔记已更新', 'success');
            navigate(`/note/${noteId}`, {}, { replace: true });
          } else {
            await api.publishNote(payload, { signal: ctx.signal });
            showPublishSuccess(title);
          }
        } catch (err) {
          if (isAbortError(err) || ctx.signal.aborted) return;
          toast(err.message || (isEdit ? '保存失败' : '发布失败'), 'error');
        } finally {
          setLoading(submitBtn, false);
        }
      });

      function showPublishSuccess(title) {
        const searchHref = title
          ? `#/search?q=${encodeURIComponent(title)}`
          : '#/search';

        openDialog({
          title: '发布成功',
          size: 'sm',
          body: `<div style="display:flex;flex-direction:column;align-items:center;gap:10px;padding:8px 0 4px;text-align:center">
            <span class="empty-icon" style="background:var(--green-soft);color:var(--green)">${icons.check()}</span>
            <h3 class="empty-title">笔记已发布</h3>
            <p class="empty-desc">稍后可在搜索中通过标题找到这篇笔记。</p>
          </div>`,
          footer: `<a class="btn btn-outline" href="#/explore" data-close>回到发现</a>
            <a class="btn btn-primary" href="${searchHref}" data-close>搜索这篇笔记</a>`,
          onMount({ dialog, close }) {
            dialog.querySelectorAll('[data-close]').forEach((el) => {
              el.addEventListener('click', () => close());
            });
          },
        });
      }

      setType(state.type);
      videoUrlInput.value = state.videoUri;
      titleCount.textContent = String(titleInput.value.length);
      renderImages();
      renderPreview();

      if (!isEdit) {
        // 新发布时默认聚焦标题（但不打断已恢复的滚动位置）
        titleInput.focus({ preventScroll: true });
      }
    }
  },
};
