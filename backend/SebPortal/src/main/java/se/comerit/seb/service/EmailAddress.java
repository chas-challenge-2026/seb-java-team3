package se.comerit.seb.service;

import java.util.Locale;

// E-postadressen är användarens inloggningsnamn och unik i databasen. Utan en gemensam normalisering
// kan Lisa@malmobygg.se och lisa@malmobygg.se bli två olika användare, och en av dem kan inte logga in.
// Därför sparas och slås e-post alltid upp i samma form: utan omgivande mellanslag och med små bokstäver.
public final class EmailAddress {

    private EmailAddress() {
    }

    // Locale.ROOT, inte datorns språk: på en turkisk dator blir annars "I" ett prickfritt "ı" och
    // adressen matchar inte längre.
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }
}
