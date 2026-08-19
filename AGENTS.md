# btv UI consistency rules

UI consistency has higher priority than local visual novelty.

- Reuse existing UI patterns before creating a new layout.
- Put navigation/back actions at the left of `PageHeader`.
- Put page-level actions in the right-side `PageHeader` action slot.
- Use `ActionBar` for regional actions and `FormActions` for forms.
- Keep at most one primary action in an action region. It must be rightmost on a row and bottommost in a compact stacked layout.
- Put secondary actions immediately to the left of the primary action.
- Put destructive actions before secondary and primary actions, using the shared destructive color and style.
- Use `DialogFooter` for dialog actions and `TableActions` for row-level edit/delete actions.
- The same semantic action must keep the same component, icon, color, size, and ordering across pages.
- Responsive layouts may change geometry, but semantic position and focus order must remain consistent.
- TV focus order must follow the visible left-to-right and top-to-bottom order.
- Before finishing a UI change, audit related pages for action position, component reuse, spacing, alignment, and action hierarchy.
- Do not introduce a new layout pattern without a clear UX reason documented in the change.
