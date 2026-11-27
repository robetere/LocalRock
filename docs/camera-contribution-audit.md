# Camera contribution readiness — preliminary audit

Reviewed snapshot: `6a81b4c1714f3f8624be0bb57a0be7fc9ad61c89` (31 commits ahead, 0 behind upstream master
`88cbeeea362b30672ec6bf1076b396a1f4470dae`; 37 changed files).
Android CI passed for that snapshot ([run 46069461179](https://github.com/robetere/LocalRock/actions/runs/46069461179)).
This is a targeted source review, not an independent security certification.

## Findings and actions

| Area | Review result |
| --- | --- |
| Upstream ancestry | Verified; no merge required at reviewed snapshot |
| Saros commands | Separate exact-model experimental drive profile; status checks; 500 ms requested movement duration; no cleaning/navigation commands via experimental controller |
| Invalid analogue update | Previously could retain old movement vector; release added with regression test |
| Video loss | Decoded-frame freshness guard; tests cover stop and no automatic restart |
| Gamepad | Neutral/explicit enable, disarm on focus/device changes, disconnect detection and rejection of stale queued events; steady axes do not provide a guaranteed heartbeat |
| Credentials | Optional Android Keystore AES-GCM, strong biometric per operation, enrollment invalidation; cancellation and hardware validation still required |
| Platform | Android CI passed before audit change; iOS shared compilation workflow added; Swift integration and hardware still pending |
| Documentation | Fork overview distinguishes upstream and development scope; screenshots pending |

## Before a pull request

- Verify Android CI on the audit commit and shared iOS compilation. Run the
  unsigned Xcode build separately, including the Swift WebRTC bridge.
- Retest S7 MaxV legacy camera, audio and movement on appropriate hardware.
- Repeat Saros driving tests: release, invalid input, video loss, controller
  disconnect, Bluetooth dropout, foreground loss, rotation and fold/unfold.
  A controller can stop delivering input without immediately being removed by
  Android; verify observed stop behaviour and document any residual limitation.
- Verify biometric cancellation, invalidated enrollment, incorrect saved PIN
  and the forget flow on an actual device.
- Check full changed-file diff for personal information, secret material and
  unnecessary workflow/configuration changes. This targeted review has not
  completed that check.
- Obtain redacted genuine screenshots and record the exact tested build.
- Discuss feature scope upstream before preparing separate small PRs.
