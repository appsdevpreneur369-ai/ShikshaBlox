# Image credits: shikshablox.com

All photographs come from **Unsplash** (searched with the "free" licence filter, so no Unsplash+ images) and are used
under the [Unsplash License](https://unsplash.com/license): free for commercial use, no attribution required.
We credit them here anyway. The files are self-hosted (WebP, resized by `scripts/gen-images.py`); nothing is hotlinked.

| File (`public/images/`) | Used for | Source |
|---|---|---|
| hero-classroom.webp | Medium schools | https://unsplash.com/photos/a-classroom-full-of-students-sitting-at-desks-I4lLt9g8i3k |
| teacher-blackboard.webp | Teachers, exams | https://unsplash.com/photos/man-in-orange-long-sleeve-shirt-sitting-beside-woman-in-blue-long-sleeve-shirt-a5An8vkc1jc |
| modern-classroom.webp | Attendance, Why us | https://unsplash.com/photos/a-group-of-children-sitting-at-desks-in-a-classroom-OcDJhBm8Jnc |
| empty-classroom.webp | Timetable | https://unsplash.com/photos/a-classroom-filled-with-wooden-desks-and-chairs-T921iC2fkJk |
| students-writing.webp | Student records | https://unsplash.com/photos/two-young-girls-sitting-at-a-desk-in-a-classroom-TcKmus54ij0 |
| students-group.webp | Small schools | https://unsplash.com/photos/group-of-people-standing-on-street-during-daytime-9TSYyblXGEA |
| early-years-reading.webp | (spare) | https://unsplash.com/photos/boy-in-red-crew-neck-shirt-holding-yellow-book-Hg-ZgKIOdpw |
| campus-playground.webp | About | https://unsplash.com/photos/children-at-the-playground-during-day-BrheXGlVRhs |
| auditorium.webp | AI co-pilots | https://unsplash.com/photos/a-group-of-people-sitting-at-a-table-in-front-of-a-projector-screen-2uX_7L_10rM |
| senior-students.webp | International schools | https://images.unsplash.com/photo-1737825101103-c35677e6bd45 |
| principal-tablet.webp | Principal, admissions, analytics | https://images.unsplash.com/photo-1656236577401-6c60e0baeed5 |
| parent-phone.webp | Parents, parent app | https://images.unsplash.com/photo-1645538165779-81d7f49baa6f |
| parent-call.webp | (spare) | https://images.unsplash.com/photo-1566847764702-a4802fd15d54 |
| school-bus.webp | Transport | https://images.unsplash.com/photo-1721839781533-cfc9811d5e54 |
| office-accounts.webp | Office & accounts, fees | https://images.unsplash.com/photo-1621857768404-fc6a3babee48 |
| office-hr.webp | HR & payroll | https://images.unsplash.com/photo-1683107695974-98a9fddb249a |
| yoga-assembly.webp | Government & Trust | https://images.unsplash.com/photo-1649008726820-d90aeb70c32e |
| students-lunch.webp | (spare) | https://images.unsplash.com/photo-1623863568368-69e4cbe6cc0b |

Other images:
- `public/product/demo-*.webp`: screenshots of Greenfield Demo School, a fictional school website running on ShikshaBlox
  staging, captured with `e2e/url-shots.mjs`. Its photos are Unsplash stock, listed in
  `backend/src/main/resources/seed/greenfield/CREDITS.md`.
- `public/schools/greenfield-logo.webp`: logo of the fictional demo school, drawn for ShikshaBlox.
- `public/brand/*` and `src/app/icon.svg`: the ShikshaBlox mark, © Aurora Blossom.

To add an image: put a ≤1600px-wide `.webp` in `public/images/`, run `python scripts/gen-images.py`, add its size to
`src/lib/images.ts`, and add a row here.
