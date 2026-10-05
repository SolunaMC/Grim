package ac.grim.grimac.utils.data;

import org.jetbrains.annotations.NotNull;

public final class TextChecks {
    private TextChecks() {
    }

    // Index of the first control character or § a vanilla client can't type, or -1
    public static int firstIllegalChar(@NotNull CharSequence text, boolean control, boolean format) {
        if (!control && !format) return -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (control && (c < 32 || c == 127) || format && c == 167) return i;
        }
        return -1;
    }
}
