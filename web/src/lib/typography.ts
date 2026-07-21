const HANGUL_PATTERN = /[\u1100-\u11ff\u3130-\u318f\ua960-\ua97f\uac00-\ud7af\ud7b0-\ud7ff]/u;

export function containsHangul(value: string): boolean {
  return HANGUL_PATTERN.test(value);
}

export function titleFontClass(value: string): "font-title-korean" | "font-title-latin" {
  return containsHangul(value) ? "font-title-korean" : "font-title-latin";
}

export function bodyFontClass(value: string): "font-body-korean" | "font-body-latin" {
  return containsHangul(value) ? "font-body-korean" : "font-body-latin";
}
