# Click Regression Checklist

Run before merging any Copilot-generated code:

## Data Layer
- [ ] ConnectionInsert fields unchanged (user_id_1, user_id_2, 
      location_id, context_tag, initiated_by, expires_at)
- [ ] No Map<String, Any> or untyped collections in Repository files
- [ ] All new @Serializable classes have @Serializable annotation

## Security
- [ ] redeem_qr_token RPC call unchanged
- [ ] Proximity score calculation unchanged
- [ ] QR token expiry (90s) unchanged

## iOS Specific  
- [ ] No Any types in iosMain code
- [ ] NFC read path still functional
- [ ] NFC write path still functional (after implementation)

## Native UI Bridging
- [ ] iOS nav buttons show system glass (not Compose `LiquidGlassPill`)
- [ ] iOS back buttons use `chevron.left` SF Symbol via `NativeBackButton`
- [ ] Chat composer +/send align with 44.dp aux buttons
- [ ] Call/overflow menus preserve confirm-dialog flows
- [ ] Connection list long-press still opens `ConnectionActionSheet`
- [ ] Android visuals unchanged (M3 fallbacks)
- [ ] `ui/components/native/README.md` matches implemented APIs

## Core Flow
- [ ] Tap → ConnectionInsert creation still fires
- [ ] 30-minute Vibe Check expiry still set
- [ ] Keep/expire mutual opt-in logic unchanged