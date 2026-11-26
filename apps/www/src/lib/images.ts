/** Intrinsic sizes of the self-hosted images (see IMAGE-CREDITS.md), so next/image never shifts layout. */
export const imageSizes: Record<string, { w: number; h: number }> = {
  "/images/auditorium.webp": { w: 1600, h: 900 },
  "/images/campus-playground.webp": { w: 1600, h: 1063 },
  "/images/early-years-reading.webp": { w: 1600, h: 1067 },
  "/images/empty-classroom.webp": { w: 1600, h: 1067 },
  "/images/hero-classroom.webp": { w: 1600, h: 1068 },
  "/images/modern-classroom.webp": { w: 1600, h: 1067 },
  "/images/office-accounts.webp": { w: 1600, h: 1067 },
  "/images/office-hr.webp": { w: 1600, h: 1067 },
  "/images/parent-call.webp": { w: 1600, h: 2240 },
  "/images/parent-phone.webp": { w: 1600, h: 1052 },
  "/images/principal-tablet.webp": { w: 1600, h: 1067 },
  "/images/school-bus.webp": { w: 1600, h: 2000 },
  "/images/senior-students.webp": { w: 1600, h: 2400 },
  "/images/students-group.webp": { w: 1600, h: 1063 },
  "/images/students-lunch.webp": { w: 1600, h: 1067 },
  "/images/students-writing.webp": { w: 1600, h: 900 },
  "/images/teacher-blackboard.webp": { w: 1600, h: 1067 },
  "/images/yoga-assembly.webp": { w: 1600, h: 1064 },
  "/product/moriah-desktop.webp": { w: 1440, h: 900 },
  "/product/moriah-gallery-desktop.webp": { w: 1440, h: 900 },
  "/product/moriah-about-desktop.webp": { w: 1440, h: 900 },
  "/product/moriah-mobile.webp": { w: 780, h: 1688 },
  "/product/moriah-gallery-mobile.webp": { w: 780, h: 1688 },
};

export function sizeOf(src: string) {
  const s = imageSizes[src];
  if (!s) throw new Error(`Unknown image ${src}: add it to src/lib/images.ts`);
  return s;
}
