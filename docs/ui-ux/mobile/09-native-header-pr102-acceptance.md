# PR #102 Native Header Acceptance

Root iOS screens use a WhatsApp-like vertical hierarchy. The persistent 40pt leading/trailing Liquid Glass controls occupy the compact action row at the top of the native header. The expanded 34pt screen title sits below that action row. As the list scrolls, the title moves continuously into the compact title plane and shrinks to 17pt; subpages remain compact with Back/Close, centered identity/title, and trailing actions.

The expanded root title is intentionally single-line for the launch system. This prevents the previous intermediate-collapse geometry in which a two-line title could be taller than the interpolating native bar, creating unsatisfiable Auto Layout and occasionally causing the title to disappear completely on physical devices.

Media does not directly snapshot and repaint the route's UIKit controls. The conversation's native binder remains the sole writer, while media publishes semantic Close/Save/Share state into that coordinator. This prevents route recompositions and overlay teardown from alternately painting xmark/chevron or share/menu symbols on the same frame sequence.

Acceptance is physical-device based. There must be no missing title at any scroll fraction, no repeated xmark/chevron swap during media dismissal, no dropped Save action, and no rematerialization flash of the Liquid Glass containers.
