# Window layout

Design notes for the dockable, splittable window layout in `com.tonic.ui.layout` and the containers built on it
(`EditorPanel`, `BottomPanel`, `ToolWindowPane`). The code carries no comments; the reasons behind its less
obvious choices, several of them fixes for bugs that are easy to reintroduce, are recorded here.

## The arrangement is a value

- The layout is a value. Dragging produces a new arrangement rather than moving Swing about; saving is writing it down; and none of it touches a component, so all of it is testable without a window — which matters, because the dragging itself is not.
- Immutable, every operation returns a new one. A drag is a hover away from being abandoned; a value that can be built, looked at and thrown away makes previewing a result straightforward rather than a matter of undoing.
- Leaves are stacks of views. Before, there were four containers whose contents were each container's own business, so the dock could be dragged and the log inside it could not. Moving a stack and moving one tab are operations on the same tree.
- Weights are fractions, not pixels, so a window opened on a different screen still divides sensibly.
- withDropped takes the stack out first and puts it back, so dragging a stack onto its own neighbour does the sensible thing rather than splitting something about to disappear. Dropping a stack on itself changes nothing: no arrangement gives that a meaning and the alternative is losing it. If the target went out with the moved stack (it was its only company), nothing changes.
- withStacked: the dropped stack stops existing (a pane of tabs dragged onto a pane of tabs means one pane with all of them); the target keeps its identity so anything remembering where a view lives still finds it. A leaf with no views to merge is put beside instead.
- withShown puts a stack back against the whole window, not beside a particular pane: which pane it used to be beside is not remembered, and the outer edge is the one place a reader can predict. Its views are taken out of wherever they are first: a view lives in one place, and showing the navigator again must bring its tree back to the edge, not a second copy.
- withViewIn takes the view out first, so moving within a stack is the same operation as moving to another, and an index always counts the tabs that will remain.
- withViewSplit is how a reader makes a new place: drag a tab to the side of a pane and there is a pane there.
- normalised(): taking a stack out leaves a one-sided divider; taking the last view out leaves an empty pane. Neither is wrong to draw once, but both accumulate — ten drags back and forth would leave ten dividers dividing nothing.
- stripped() is kept apart from without() so every operation tidies exactly once, at the end; a drag takes a stack out and puts it back, and only the result of both is worth tidying.
- removeView keeps an emptied stack for tidy() to decide about: whether an empty stack is worth drawing is one rule in one place; stating it twice means the two eventually disagree.
- tidy() keeps an emptied side panel: elsewhere an empty pane is a rectangle with nothing in it, but down a side it is where a reader drags things back to, and without it there is no way back.

## Stacks

- The four shipped stacks are places, not components: anything can be dragged into any of them, and dragging a tab to an edge makes more.
- Keys are the ones the file always used: the documents stack is "editor" and the bottom stack is "dock" on disk, so older files read without migration.
- Home edge and weight are for a stack put back after being taken off the window (view menu, or something opening in it): the edge it ships on is where a reader predicts to find it.
- Side panels (navigator, tools): names run up a column, because a row of tabs across a pane a couple of hundred wide is a row of nothing, and that column is what remains when put away, so the two states are one picture at two widths. They keep their place when emptied: a side that vanished with its last tab could never be dragged back to. It is said of the stack, not read off its position: the middle is beside something horizontally whenever the dock is closed, and its tabs turning on their side then would be the window rearranging itself over something done elsewhere.
- homeWeight is the stack's own share, not the divider position. They coincide on left/top and are complements on right/bottom; reading it the other way once brought the dock back covering seven tenths of the editor. Shares match the shipped arrangement so a stack that went away returns at its size.

## Views and tabs

- TabLook is separate from the view because the same view is drawn differently over its life (renamed, icon changes) while it stays the same view.
- ViewSpec: whatever owns a view hands the host one spec; where it lands, what its tab says and which stacks take it are answered from the spec alone, so adding a view is a call, not an edit to the layout.
- Kind: a document is what a reader came to read (a class, a resource) and belongs in the middle or a window of its own; a utility is about the document and goes anywhere. A stack a reader made takes anything.

## The shipped arrangement

- shipped(): navigator | ((documents over dock) | tools). Written down, not constructed; it is what a new reader gets and what Reset returns to.
- The tools side is in the shipped tree even with nothing in it: it is what a reader drags things back to.
- asLaunched(): dock and tools always open put away, whatever the file says; a window opening with the console across a third of it spends space before anybody asked. The navigator stays open; it is where a reader starts.
- contentsOf(): the shipped membership is the only membership written down. A view opens where it belongs and is moved from there, so the file carries the shape of the window, not what was in it. Tools have no shipped members: the stack arrives when the first tool opens (at its home edge and share) and goes when the last closes.
- The dock is a registry, not a fixed set: anything wanting somewhere for output asks for a tab, so dock views are named by title (dock:<title>).
- ViewId equality by key is what makes "open it" mean "raise it if already somewhere" rather than "make another".

## One owner of where a view is (ViewHost)

- The one answer to where a view is. Everything that opens something goes through it, which makes "open it" mean "raise the open one". A second place able to answer could disagree, and every disagreement looks the same to a reader: a tab that will not come back, or two tabs on one thing. An interface so owners of views need not know what draws the window.
- open(): a view already open stays where the reader put it; only one that is not open lands in its spec's home.
- reveal(): what a button naming a view and a click on its tab both do. The control that opened something puts it away the second time; a view not in front comes forward opening its stack, so a button never has to be pressed twice to be obeyed once.
- moveTo(): dragging a tab, for the things that ask in words (menu item, keystroke).
- Listeners hear every view, because a stack holds whatever was dragged into it; each acts on the views it owns. headerPressed's inFront is the gesture the dock reads as "put this away".

## Drawing and dragging (LayoutController)

- It used to build three nested split panes in a fixed order, and every collapse named a region by its side: the dock's was height minus strip, the tool window's width minus stripe, the navigator's a divider at zero. Three pieces of arithmetic each assumed nothing would move. Now there is one: a pane is collapsed by putting the divider it shares with the rest at the extent that pane reports, measured from whichever side it is on. The same rule serves every region and edges none has been on before.
- The arrangement is rebuilt into a holder that stays put. Swing has one parent per component, so moving a region is removing and adding it; the components are the same throughout, and only the scaffolding of splits and tabs around them is rebuilt.
- Rebuilding is cheap enough to do mid-drag (measured in EStudio at 0.3 ms average and 0.9 ms worst, over four thousand lines of highlighted code and a three-thousand-node tree: a fiftieth of a frame). That is what lets rearrange mode show the arrangement a drop would produce rather than an outline.
- A put-away pane with no report of its own shrinks to ViewStackPane.ACROSS: wide enough to grab and to draw a name in. Below 60px a pane is not worth drawing and cannot be dragged back.
- specs is keyed by view, not pane: a pane is a place, this is what is in it. Which pane is the arrangement's to say.
- Strips are kept, not rebuilt: a rebuild happens every pixel of a drag, and rebuilding a strip would re-parent every tab (not free for a long listing) and lose the reader's place.
- Torn-out windows are outside the tree: a view is in the tree or in one torn-out window, never both. That is the whole of the bookkeeping.
- madeFor(): a stack nothing asked for arrives put away. It is how the tools arrive (the side is whatever registered a tool, which no preset or file can know) and why a fresh window does not open with a tool across a third of it.
- keepTheDividers() before every change: a dragged divider moves the drawing and nothing else until something asks, so a change built on the last-set arrangement would put it back where it was at the last rebuild. Opening the dock undid the height a reader had just given it, every time.
- show(): anything open that the new shape does not name goes back to its home stack. Changing the arrangement changes where things are, not what is open, and an arrangement cannot name what it never heard of: a preset written long ago knows nothing about the tools registered today.
- isCollapsed(): a stack with nothing in it counts as put away whatever the arrangement says. An emptied side keeps its place, and an empty pane taking a fifth of the window shows nothing.
- toggle(): three states, because a region has three (absent, present but put away, open). Repeated presses walk the two useful ones.
- The bottom toolbar sits under the whole working area: it is about the window, not any one region.
- build(): side panels get upright names only when beside something horizontally, against the window edge. A stack asks for no size (preferred 0 by 0) so a split divides by its weight rather than by what is in the tabs; the dock once came up taking a third of the window because the log's preferred height was all a first layout had to go on.
- Dividers are placed from a resize listener, not at build time: a split has no size until laid out, so arithmetic run then resolves against zero and hands the whole window to one side. The listener removes itself once an ordinary divider is placed, because from then on the divider is the reader's. Left listening, it re-imposed the arrangement over every drag and a dragged dock sprang back.
- stripFor() is public because a strip is where a reader points at a stack: something dropped on the dock is dropped on the dock's strip, whatever it holds.
- wire() gives every strip, in the window or torn out, the same behaviour. That is the point of there being one strip.
- fill() leaves what is already there alone (a rebuild must not disturb the tab being read), re-applies tab order (adding does not move existing tabs, so a reordered stack would come back in its old order), and keeps the tab the reader had in front; the arrangement only says which tab a stack opens on.
- tearOut() shows the window only when this window is showing: a window torn out of one nobody is looking at is not one to put in front of anybody. It is also what lets tear-out be tested without anything appearing.
- closeTornOut() sends each window's views home itself rather than relying on the window's closing event: a window never put on screen has no closing event, and a tab lost that way is a tab a reader lost. It also disposes the drag ghost, a window the shell would otherwise wait on at exit.
- putBack(): closing a torn-out window is never how a reader loses something. Views land where they would if opened afresh, since where they were before tear-out is not remembered and the shipped home is predictable.
- pressed(): clicking the name in front of a side closes the side (the control that opened something puts it away the second time); any other name swaps to it, opening the side if shut. The strip has already brought the name forward, so only the room is left to decide.
- sentHome(): closing a tool pulled into the middle sends it back to its side rather than closing it; the reader is done reading it there, not done with it. One already home closes normally.
- Nothing moves while a tab is carried. The arrangement is untouched until release; the ghost and outline promise instead. The header under the gesture holds the mouse, and reflowing under it once took the header out of the window mid-gesture so the release never arrived.
- The package-private beginCarrying(view, panes) overload exists so drag decisions can be tested against given rectangles, the half that can be wrong invisibly.
- Panes are measured once, at the press, in screen coordinates (window, torn-out windows and the desktop between). Re-read on every hover, a drag would chase itself: a window raised under the pointer would change the target without the hand moving.
- A point in a tab row is a place among the tabs whichever quarter of the pane the row sits in. Read as an edge it would split the pane, which dropping on a row never means.
- aimAt() works out and remembers what release would do, and changes nothing. That is the difference from dragging whole panes in rearrange mode.
- drop(): over nothing that takes it, or with Ctrl held, the tab comes away into a window of its own.
- landingIndex(): the caret is read off the row as it stands, with the dragged tab still in it, so moving right within its own stack would aim one place too far; the index is corrected. Dropped into the body of its own stack, it stays where it is rather than going to the end.
- stopCarrying(): nothing to put back, since the window showed what it was showing all along. Abandoning only takes down what was drawn over it.
- place(): the one piece of collapse arithmetic. A put-away pane gets the extent it reports and the rest gets what is left, measured from its side of the divider. It returns whether the divider is now the reader's: one holding a put-away pane keeps being worked out (what it shows depends on its pane); an ordinary one is placed once, and the split keeps its share across resizes on its own.
- A put-away stack keeps exactly its own row or column of tabs, one rule now that every stack draws its tabs on its own edge (it used to be three rules). With no tabs it takes nothing: a strip nobody can open is a grey band a reader can neither open nor close nor account for.
- measured() ignores a divider holding a put-away pane: that position is where the collapse put it, not where anybody dragged it, and saving it would return as a pane the width of its own strip.

## Tab strips (ViewStackPane)

- One per stack and the only tab strip in the window: editor, dock and torn-out windows all use it, so a tab looks and behaves the same wherever a reader put it.
- It draws and reports; it does not decide. Closing is a request to the listener, because what closing means differs by owner (the dock keeps a closed panel registered so a log hidden during a run still has the run in it; a closed class tab is gone). The one thing it does alone is bring a pressed tab forward, which nobody would want back.
- ACROSS is the width of a column of upright names. It is not called WIDTH: every component inherits ImageObserver.WIDTH (= 1), and an inherited name beats an enclosing one, so the constant read as one pixel inside the header and the column came out a sliver.
- DragOut: the strip reorders within itself and hands anything further to whatever arranges the window, since where else a tab could go is a whole-window question. It is told in screen coordinates because once a drop is previewed the dragged tab has moved to another strip, and the header the gesture began on is no longer in the window.
- Swing binds Ctrl+PageUp/PageDown on a tabbed pane itself, and an ancestor binding beats the window's, so the built-in would answer while a listing has focus and the application's binding everywhere else. The strip gives those keys up so there is one answer per key.
- Upright columns drop the look-and-feel's generous tab padding: right for a row across a pane, most of the width of a column down its side.
- An empty upright column is still one column wide (it is what a reader drags things back to); an empty row of tabs is nothing.
- holds() asks the tabbed pane, not the strip's own maps: a component has one parent, so giving a panel to another strip removes it here, and the maps would claim it was in two places.
- Tab gestures are one handler because the pieces must agree on what the gesture was. Selection happens on press (what makes a tab feel answered); everything else waits for release, and a release after a drag is not a click. A press used to be reported immediately, a side panel read it as "put me away", and putting a side away rebuilds it — removing the header from under the hand still holding it, so a side's name could not be dragged. headerPressed is reported last for the same reason.
- The menu is offered on both press and release because which one is the popup trigger is the platform's business. Close is on the click: a press ending elsewhere is not a click on this tab.
- Labels get the listeners as well as the header panel: the layout gives children the pixels a reader actually presses.
- No gap before a pinned tab: the Welcome page sits first so a reader can always find it.
- Row vs body: a point in the tab row is a place among the tabs; a point in the body below is somewhere to dock, which is how a pane is split.
- order() keeps the front tab in front: a reorder is not about what the reader is reading.
- movable is read off the header rather than stored separately, so the two cannot fall out of step.
- Upright headers have no close button or icon (no room; the menu offers closing).
- Headers restyle themselves on theme change: a header outlives every theme switch made while its tab is open.

## Edges

- Edge is its own type because the same five answers describe a stack dropped beside a stack and a tab beside a tab; only what moves differs.

## Drop targets (DropTarget)

- Kept apart from drawing and dragging because it is the part that can be wrong invisibly: a drop landing one pane over, or an edge a third of the pane on one side and two thirds on the other, reads as the window ignoring the gesture. It is arithmetic, tested as arithmetic.
- EDGE_SHARE 0.25: the middle half of a pane stacks. Stacking is the harder thing to aim at — a split has a whole window edge behind it to stop the pointer, the middle has nothing — so the middle is the larger target.
- SMALLEST_TO_SPLIT 80px: a put-away pane is a strip a few pixels across; five targets in it would be one pixel each and none reachable, so it only stacks.
- At most one pane contains a point: the tree divides the window without overlap and a rectangle holds points up to its edge but not on it, so panes meeting at a seam never both claim it. No tie-break rule is needed.
- A stack is not a target for itself: dropping it on its own pane means nothing, and offering it reads as though it might.
- A view's own pane does count: its middle is a reorder, its edge splits it, both of which readers mean often. Panes that will not take the view offer nothing rather than lighting up and then refusing; what counts as a document is the caller's business, passed in as a predicate.
- The nearest edge is found by comparing distances rather than testing edges in an order, so a corner does not belong to whichever edge happened to be asked first.
- zoneIn: the middle takes the whole pane (a stack gaining a tab), a side takes half (the weight a split is made with), so the outline says what will happen rather than roughly where.
- The caller mints the stack id a split creates, so two drags in a row never name their stacks the same.

## Drag feedback

- DragFeedback is a value separate from painting so the landing (zone one pane over, caret in the wrong gap) can be tested without a window. Screen coordinates, because a drag crosses the main window, torn-out windows and the desktop between them.

## The tab drag overlay

- A glass pane, like the rearrange overlay: draws over the window without taking a pixel, so nothing moves while a reader decides. Unlike rearrange mode it promises rather than performs: the tab header under the gesture holds the mouse and must stay put.
- One per window, each told the same screen-coordinate feedback; what falls outside a window is not drawn there, so the outline appears on whichever window the pointer is over without anything deciding which.
- lower() restores the previous glass pane rather than the default: rearrange mode leaves its overlay installed and hidden, and removing it would be a mode nobody could turn on again.
- The insertion mark is drawn over the wash: the gap a tab lands in is the more exact answer, so it is the one that reads.

## The drag ghost

- A window of its own rather than painting on the source window, so it stays visible over another window, a torn-out one, or nothing at all. The last matters most: dragging off everything is how a reader asks for a new window, and a gesture that goes invisible exactly when it is about to do something is one nobody trusts. Over no target it says "New window", the one outcome no outline can show.
- It sits offset from the pointer so it does not cover it. A platform without translucency gets a solid chip.

## Torn-out windows (FloatingStack)

- What tearing a tab out gives a reader: a symbol table beside a listing is too narrow to be useful, and letting them move it beats guessing a width that suits glancing and reading.
- A stack, not a single view, so a torn-out window is somewhere to put the next tab; a ViewStackPane, so a tab in it behaves like any other.
- Closing it is never how a reader loses something: whatever is still in it goes back to the stack it belongs in.

## Rearrange mode (RearrangeOverlay)

- A glass pane, not part of the layout, so the window looks exactly as usual until the mode is on. Grab bars are painted, not components: a component header would take a strip of height from every region the moment the mode began, rearranging the window before anybody dragged anything.
- The preview is the arrangement itself: as the pointer crosses a drop target the working area is rebuilt to what releasing there would produce (under a millisecond, so per hover). An outline would be cheaper but only a drawing of a promise. Esc puts back what was there; a reader who dragged something somewhere unintended needs one key, not the memory of where it was.
- The pointer is measured against where panes were when the drag began. Reading what is currently drawn makes the drag chase itself: the preview moves the panes, the pointer is over a different one, a different preview moves them back, and the window flickers between two arrangements without the hand moving. Geometry is frozen at the press and previews are computed from the arrangement at the press.
- For the same reason nothing is outlined during a drag: the frozen panes and the on-screen panes differ, and drawing one over the other would put two answers on the window with the wrong one under the pointer. During a drag it draws a wash and the name of what is carried; the reflow is the answer.
- Over no target the window returns to how it was, so letting go outside every pane leaves it unchanged rather than wherever the last hover put it. After a drop, what is on screen becomes the new baseline for the next drag.
- The cursor repaints only when it changes: repainting the window on every pixel of an idle pointer is work nobody asked for and its own kind of flicker.
- A pane a reader made is labelled with what is in it: a pane called "stack-3" says nothing.
- The bar's close box takes a stack off the window; Reset Layout brings everything back, so nothing is lost permanently.

## The dock and the tool windows

- Both are registries, not panes. Each used to own its own tabbed pane or card layout with painted headers; their panels are now views like any other, so a reader can drag the console beside the code, put bookmarks down a side, or tear run output into its own window. What remains is which panel answers to which name, and what is particular to each.
- Tools are opened as soon as the layout exists: the shell registers them while still assembling itself, before anything is arranged. Dock tabs are registered eagerly but opened only when asked for: they are answers to things a reader did, and opening them because the window was built would show four panels nobody asked for.
- Registering a panel under a title already in use replaces it, closing the old tab first so the new panel is what opens; a contributor that rebuilds its panel does not end up with two.
- toggleTab is not "close it if open": asking for the bookmarks while looking at the console means show the bookmarks. It opens, or reveals, and only puts the dock away where that tab was already in front.
- Find Usages keeps one tab per target: searching the same thing twice is the same question asked again, not a second answer to keep beside the first.
- Plugin tabs and tools with a taken title get a number: a plugin cannot know what is already there, and two tabs answering to one name is a tab that cannot be closed by name.
- Bookmarks and Comments are built against a project, so closeAllTabs drops them and the next project gets its own.
- Pressing a dock tab: the one in front puts the dock away, any tab lets it out. The control a reader opened something with should put it away the second time.
- A tool whose tab was shut is closed, not merely hidden: it stays registered, and asking for it again brings it back.
- The tool menu's Move to Tab and Move to Window exist for a reader who would rather be told than guess that an upright name is something that can be picked up; dragging does the same and more.

- ToolWindowMover: moving a tool is moving a view; it keeps its identity, so there is no "dock back" control — the tab it became drags like any other.
## The layout file (LayoutBook)

- Anything that cannot be understood is dropped, never fatal: a stack renamed away, a tree with a hole, a hand-edited file. The arrangement is worth losing; the window is not.
- The whole arrangement is written, not a difference from the shipped one: a difference between two trees is nothing anybody could read in the file or reason about when it went wrong.
- Sizes are fractions: a window arranged on one screen and opened on another has to divide sensibly.
- The shape is remembered, the contents are not. A view is opened by asking for it, and asking is what says where it goes.
- The version number is read but never refused: a file from a later version is read for whatever makes sense rather than thrown away over a number.
- load(): a tree that lost a stack to a rename is still worth having; one that lost all of them is not an arrangement, so the shipped one is used.
- read(): one seen-set for the whole tree, not per node: a file naming a stack in two places would otherwise give a window with two navigators and no way to tell them apart. A strip named twice would be drawn twice.
- readNode(): a divider whose sides are both gone is gone; one with a side left becomes that side. Same rule as Arrangement when a stack is removed: a file naming something this build no longer has costs that stack and nothing else.
- Four leaf spellings: stack and stripe (this build), pane and tabs (the build before, when a leaf was one of four containers). A pane is that stack; a tabs of several is the first of them, since what the others held has to be asked for again anyway.
- Weights are clamped to 0.1-0.9 (withinReach): a weight outside that is a divider dragged off the window, a pane nobody can get back.
- leaf(): a stack with nothing shipped in it is not read, except a side panel, which keeps its place empty so what registers fills it. The tools are that case: what is on the right is whatever registered a tool, which is nothing a file can say.

## Writing files

- ConfigFile writes beside the file then moves over it: a layout that will not parse is replaced by the defaults, so a half-written file would lose the fact that there was an arrangement at all. Falls back to a non-atomic move where the filesystem cannot promise atomicity — still a smaller window than truncating in place. Failures are swallowed: nothing useful to do, not worth interrupting anyone.
- Always UTF-8, stated not inherited: the platform charset makes a file that reads back differently elsewhere.
- JStudioPaths exists because the plugins dir, the close diagnostic and the layout each spelled ~/.jstudio their own way.
- Shipped stack keys (StackId) are permanent: they are in layout.json the moment anybody rearranges anything. Dragged-out stacks get generated keys (stack-N).

## Tests

- Everything above the Swing layer is a value or arithmetic (ArrangementTest, DropTargetTest, LayoutBookTest), so none of those tests constructs a component.
- The Swing tests build components but never show them. WindowViewsTest lays a window out without showing it, because the put-away arithmetic needs real sizes.
- WindowViewsTest drives a LayoutController directly rather than a MainFrame: constructing the frame schedules an update check against GitHub and loads whatever plugins the person running the tests has installed.
- Drag decisions are tested through the package-private beginCarrying(view, panes) overload against supplied rectangles.
- TabGestureTest covers the header gestures as one handler because the pieces must agree on what a gesture was. Middle-click once selected a tab instead of closing it (no button-two handling existed and the press listener selected on any button); a gesture doing something other than what it is for is worse than one doing nothing.
- A stack dragged from a side to the bottom must turn its tabs with it; left upright, a column of names would lie on its back across the top of a pane.
