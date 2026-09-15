# fix-duplicate-label-print

Fix duplicate/lagged label print in the Sunmi HTTP printer server: replace the fragile buffer-transaction printBitmap path with a direct printBitmap + paper feed to eject each label exactly once
