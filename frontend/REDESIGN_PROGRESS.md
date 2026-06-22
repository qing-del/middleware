# CORE NODE Minimal Redesign Progress

## Direction

- Visual direction: CORE NODE owned brand, Notion-inspired minimal black/white workspace, not a Notion clone.
- Motion direction: lightweight and practical. Login keeps a subtle monochrome canvas particle field that reacts to the mouse and respects `prefers-reduced-motion`.
- No new UI library and no GSAP dependency.

## Completed

- Added global design system modules under `src/styles/`:
  - `tokens.css`
  - `base.css`
  - `components.css`
  - `forms.css`
  - `tables.css`
  - `article.css`
  - `arco-overrides.css`
  - `motion.css`
- Updated `src/main.ts` to load the new style modules after Tailwind and Arco, so they can override legacy page styles.
- Reduced `src/style.css` back to Tailwind directives plus a minimal reset.
- Rebuilt the main shells:
  - `src/layouts/UserLayout.vue`
  - `src/layouts/AdminLayout.vue`
  - `src/layouts/GuestLayout.vue`
- Updated auth entry surfaces:
  - `src/views/Login.vue`
  - `src/views/ActivateAccount.vue`
- Updated shared components:
  - `src/components/AudioTaskModal.vue`
  - `src/components/EmailComposer.vue`
- Second cleanup pass:
  - `src/views/user/Dashboard.vue`
  - `src/views/admin/Dashboard.vue`
  - `src/views/user/NoteEdit.vue`
- Fixed the legacy gradient compatibility fallback in `src/styles/base.css`, so non-text gradients degrade to the monochrome accent instead of becoming visually empty.
- Updated the NoteEdit CodeMirror theme from a hard-coded dark editor to the shared minimal light tokens.
- Added global compatibility rules for legacy classes:
  - `.glass-panel`
  - `.glass-card`
  - `.glass-checkbox`
  - `.modal-card`
  - old glowing shadows
  - dark background utilities
  - decorative blur orbs
  - gradient text
  - Arco Message/Modal/Button styles

## Verification

- `cd frontend && npm.cmd run build` passed after the first implementation pass.
- `cd frontend && npm.cmd run build` passed again after shared component cleanup.
- `cd frontend && npm.cmd run build` passed after the second cleanup pass.
- Current build has the existing Vite chunk size warnings only.

## Known Residuals

- Many view templates still contain old class names such as `glass-panel`, `shadow-[0_0...]`, `bg-gradient-*`, and decorative blur nodes.
- These are currently normalized by global compatibility CSS, but a future cleanup pass should remove them from templates and scoped styles page by page.
- High-risk pages to clean manually next:
  - `src/views/Login.vue` still has old template utility classes even though the scoped CSS forces the final look toward minimal.
  - `src/views/user/Images.vue` should be split carefully because the gallery, upload modal, preview modal, and backlinks modal are all in one large template.
  - `src/views/user/Notes.vue`
  - `src/views/user/NoteDetail.vue`
  - `src/views/admin/Audit.vue`
  - `src/components/AudioTaskModal.vue` is visually cleaned, but should still be checked in the pages that mount it.
  - `src/components/EmailComposer.vue` is visually cleaned, but should still be checked in admin/user mail flows.

## Next Recommended Pass

- Replace repeated page header/filter/table/card markup with reusable class patterns from the global modules.
- Remove local `.glass-panel`, `.glass-checkbox`, and `.modal-card` style blocks once each page is manually cleaned.
- Visually inspect desktop and mobile routes for:
  - login/register/activation
  - user dashboard
  - user notes list
  - note edit/detail
  - admin dashboard/audit
  - guest notes/detail
  - upload/audio/email modals
