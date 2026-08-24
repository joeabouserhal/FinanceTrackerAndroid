feat: add Report page with filtered category breakdowns

- New Report tab: per-currency donut charts (spending/earning toggle),
  totals strip (IN/OUT/NET), and a per-category breakdown with counts,
  amounts, and percentages
- Report reuses the Transactions filter panel (extracted to a shared
  FilterPanel.kt) — type, sort, date, category, currency, account
- SPENDING/EARNING toggle scopes the category list to the selected type
  and prunes stale category selections when switching
- Removed the search bar from Report; view toggle lives inside the
  expanded filter area with proper section spacing

fix: preset add-flow navigation and prefill races

- Preset category/currency/account no longer get clobbered while their
  lists are loading (normalization effects wait for non-empty data)
- Back gesture from the preset-prefilled form returns to "Add from
  preset"; X and SAVE skip the picker and land on the origin page

feat: amount field shows the selected currency symbol while typing
