package com.serdar.user.service;

import com.serdar.common.ServiceException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class SocialUrlValidator {

    private static final Set<String> PLATFORMS = Set.of(
            "youtube", "spotify", "linkedin", "instagram", "facebook",
            "steam", "github", "x", "twitch", "tiktok"
    );

    private static final Pattern YOUTUBE = Pattern.compile("^/@[^/]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPOTIFY = Pattern.compile("^/(?:intl-[a-z]{2}/)?user/[^/]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINKEDIN = Pattern.compile("^/in/[^/]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTAGRAM = Pattern.compile("^/(?!p/|reel/|stories/)[^/]+/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FACEBOOK_PAGE = Pattern.compile("^/[^/]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STEAM = Pattern.compile("^/(?:id/[^/]+|profiles/\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB = Pattern.compile("^/[^/]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern X = Pattern.compile("^/[^/]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TWITCH = Pattern.compile("^/[^/]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIKTOK = Pattern.compile("^/@[^/]+$", Pattern.CASE_INSENSITIVE);

    private SocialUrlValidator() {}

    static boolean isSupportedPlatform(String platform) {
        return platform != null && PLATFORMS.contains(platform);
    }

    static String validateAndNormalize(String platform, String rawUrl) {
        if (!isSupportedPlatform(platform)) {
            throw invalid("Unsupported social platform.");
        }
        if (rawUrl == null || rawUrl.isBlank()) {
            throw invalid("Enter a link.");
        }

        URI uri = parseUrl(rawUrl.trim());
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid("Enter a valid URL.");
        }

        if (!isProfilePath(platform, uri)) {
            throw invalid(hint(platform));
        }

        return cleanUrl(platform, uri);
    }

    private static URI parseUrl(String raw) {
        String normalized = raw;
        if (!normalized.toLowerCase(Locale.ROOT).startsWith("http://")
                && !normalized.toLowerCase(Locale.ROOT).startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        try {
            return new URI(normalized);
        } catch (URISyntaxException e) {
            throw invalid("Enter a valid URL.");
        }
    }

    private static String host(URI uri) {
        return uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    }

    private static boolean hostMatches(URI uri, String... hosts) {
        String h = host(uri);
        for (String candidate : hosts) {
            if (h.equals(candidate)) return true;
        }
        return false;
    }

    private static String path(URI uri) {
        String p = uri.getPath();
        if (p == null || p.isBlank()) return "/";
        p = p.replaceAll("/+$", "");
        return p.isEmpty() ? "/" : p;
    }

    private static boolean isProfilePath(String platform, URI uri) {
        String p = path(uri);
        return switch (platform) {
            case "youtube" -> hostMatches(uri, "youtube.com", "m.youtube.com") && YOUTUBE.matcher(p).matches();
            case "spotify" -> hostMatches(uri, "open.spotify.com") && SPOTIFY.matcher(p).matches();
            case "linkedin" -> hostMatches(uri, "linkedin.com") && LINKEDIN.matcher(p).matches();
            case "instagram" -> {
                if (!hostMatches(uri, "instagram.com")) yield false;
                if (!INSTAGRAM.matcher(p).matches()) yield false;
                yield p.split("/").length <= 2;
            }
            case "facebook" -> {
                if (!hostMatches(uri, "facebook.com", "m.facebook.com")) yield false;
                if ("/profile.php".equalsIgnoreCase(p)) {
                    yield queryParam(uri, "id") != null;
                }
                String slug = p.length() > 1 ? p.substring(1).toLowerCase(Locale.ROOT) : "";
                yield FACEBOOK_PAGE.matcher(p).matches()
                        && !Set.of("login", "watch", "marketplace").contains(slug);
            }
            case "steam" -> hostMatches(uri, "steamcommunity.com") && STEAM.matcher(p).matches();
            case "github" -> {
                if (!hostMatches(uri, "github.com")) yield false;
                if (!GITHUB.matcher(p).matches()) yield false;
                String slug = p.substring(1).toLowerCase(Locale.ROOT);
                yield !Set.of("settings", "login", "signup", "features").contains(slug);
            }
            case "x" -> {
                if (!hostMatches(uri, "x.com", "twitter.com")) yield false;
                if (!X.matcher(p).matches()) yield false;
                String slug = p.substring(1).toLowerCase(Locale.ROOT);
                yield !Set.of("home", "explore", "settings", "i").contains(slug);
            }
            case "twitch" -> hostMatches(uri, "twitch.tv") && TWITCH.matcher(p).matches();
            case "tiktok" -> hostMatches(uri, "tiktok.com") && TIKTOK.matcher(p).matches();
            default -> false;
        };
    }

    private static String cleanUrl(String platform, URI uri) {
        String p = path(uri);
        if ("facebook".equals(platform) && "/profile.php".equalsIgnoreCase(p)) {
            return uri.getScheme() + "://" + uri.getHost() + p + "?id=" + queryParam(uri, "id");
        }
        return uri.getScheme() + "://" + uri.getHost() + p;
    }

    private static String queryParam(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) return kv[1];
        }
        return null;
    }

    private static String hint(String platform) {
        return switch (platform) {
            case "youtube" -> "Use a youtube.com/@ profile link.";
            case "spotify" -> "Use an open.spotify.com/user/ profile link.";
            case "linkedin" -> "Use a linkedin.com/in/ profile link.";
            case "instagram" -> "Use an instagram.com profile link.";
            case "facebook" -> "Use a facebook.com profile link.";
            case "steam" -> "Use a steamcommunity.com/id/ or /profiles/ link.";
            case "github" -> "Use a github.com profile link.";
            case "x" -> "Use an x.com or twitter.com profile link.";
            case "twitch" -> "Use a twitch.tv profile link.";
            case "tiktok" -> "Use a tiktok.com/@ profile link.";
            default -> "Enter a valid profile link.";
        };
    }

    private static ServiceException invalid(String message) {
        return ServiceException.invalid(message);
    }
}
