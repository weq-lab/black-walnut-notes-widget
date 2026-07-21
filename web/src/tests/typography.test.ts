import { describe, expect, it } from "vitest";
import { bodyFontClass, containsHangul, titleFontClass } from "../lib/typography";

describe("note typography language selection", () => {
  it("detects every supported Hangul block", () => {
    for (const sample of ["ᄀ", "ㄱ", "ꥠ", "한", "ힰ"]) expect(containsHangul(sample)).toBe(true);
  });

  it("uses the Korean family for mixed text", () => {
    expect(titleFontClass("오늘 Deep Work 3시간")).toBe("font-title-korean");
    expect(bodyFontClass("Focus 후 휴식")).toBe("font-body-korean");
  });

  it("uses the Latin family when Hangul is absent", () => {
    expect(titleFontClass("Make Each Day Count")).toBe("font-title-latin");
    expect(bodyFontClass("Focus, patience, and consistency.")).toBe("font-body-latin");
  });
});
