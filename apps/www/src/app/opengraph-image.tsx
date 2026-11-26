import { ImageResponse } from "next/og";

export const alt = "ShikshaBlox: the School ERP that grows with your school";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

// Generated once at build time and shared by every page (pages inherit the root Open Graph image).
export default function OgImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          padding: 72,
          background: "linear-gradient(135deg, #0d1030 0%, #1e1b4b 55%, #3730a3 100%)",
          color: "white",
          fontFamily: "sans-serif",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
          <div style={{ display: "flex", flexWrap: "wrap", width: 72, height: 72, gap: 6 }}>
            <div style={{ width: 33, height: 33, borderRadius: 9, background: "transparent" }} />
            <div style={{ width: 33, height: 33, borderRadius: 9, background: "#f59e0b" }} />
            <div style={{ width: 33, height: 33, borderRadius: 9, background: "#4338ca", border: "2px solid #6366f1" }} />
            <div style={{ width: 33, height: 33, borderRadius: 9, background: "#6366f1" }} />
          </div>
          <div style={{ fontSize: 44, fontWeight: 800, display: "flex" }}>
            Shiksha<span style={{ color: "#fcd34d" }}>Blox</span>
          </div>
        </div>
        <div style={{ display: "flex", flexDirection: "column" }}>
          <div style={{ fontSize: 76, fontWeight: 800, lineHeight: 1.05, letterSpacing: -2 }}>The School ERP that</div>
          <div style={{ fontSize: 76, fontWeight: 800, lineHeight: 1.05, letterSpacing: -2, color: "#fcd34d" }}>grows with your school</div>
          <div style={{ marginTop: 28, fontSize: 30, color: "#c7d2fe" }}>
            Website & CMS · Admissions · Attendance · Fees · Exams · Parent app · WhatsApp
          </div>
        </div>
        <div style={{ fontSize: 24, color: "#a5b4fc" }}>shikshablox.com · A School ERP Software product of Aurora Blossom</div>
      </div>
    ),
    size,
  );
}
