import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

enum Livello {
    CRITICO(15),
    ALTO(30),
    MEDIO(60),
    BASSO(120);

    final int minutiLavorazione;

    Livello(int minutiLavorazione) {
        this.minutiLavorazione = minutiLavorazione;
    }

    boolean urgente() {
        return this == CRITICO || this == ALTO;
    }
}

class Ticket implements Comparable<Ticket> {
    final String id;
    final String descrizione;
    final Livello livello;
    final long timestampArrivo;

    Ticket(String id, String descrizione, Livello livello, long timestampArrivo) {
        this.id = id;
        this.descrizione = descrizione;
        this.livello = livello;
        this.timestampArrivo = timestampArrivo;
    }

    @Override
    public int compareTo(Ticket altro) {
        int confronto = Integer.compare(this.livello.ordinal(), altro.livello.ordinal());
        if (confronto != 0) {
            return confronto;
        }
        confronto = Long.compare(this.timestampArrivo, altro.timestampArrivo);
        if (confronto != 0) {
            return confronto;
        }
        return this.id.compareTo(altro.id);
    }
}

public class Esercizio3 {
    private static final int MAX_URGENTI_CONSECUTIVI = 5;
    private static final int PAUSA_MINUTI = 10;

    static PriorityQueue<Ticket> leggiTicket(Path file, List<String> log) throws IOException {
        PriorityQueue<Ticket> coda = new PriorityQueue<>();
        Set<String> idVisti = new HashSet<>();

        try (BufferedReader lettore = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String riga;
            int numeroRiga = 0;

            while ((riga = lettore.readLine()) != null) {
                numeroRiga++;
                if (numeroRiga == 1 || riga.trim().isEmpty()) {
                    continue;
                }

                String[] campi = riga.split(",", -1);
                if (campi.length != 4) {
                    log.add("Riga " + numeroRiga + ": numero di campi errato (" + campi.length
                            + " invece di 4) -> " + riga);
                    continue;
                }

                String id = campi[0].trim();
                String descrizione = campi[1].trim();
                String livelloTesto = campi[2].trim();
                String timestampTesto = campi[3].trim();

                if (id.isEmpty() || descrizione.isEmpty() || livelloTesto.isEmpty() || timestampTesto.isEmpty()) {
                    log.add("Riga " + numeroRiga + ": campo vuoto -> " + riga);
                    continue;
                }

                Livello livello;
                try {
                    livello = Livello.valueOf(livelloTesto);
                } catch (IllegalArgumentException e) {
                    log.add("Riga " + numeroRiga + ": livello non valido '" + livelloTesto
                            + "' (ticket " + id + ")");
                    continue;
                }

                long timestamp;
                try {
                    timestamp = Long.parseLong(timestampTesto);
                } catch (NumberFormatException e) {
                    log.add("Riga " + numeroRiga + ": timestamp non numerico '" + timestampTesto
                            + "' (ticket " + id + ")");
                    continue;
                }

                if (!idVisti.add(id)) {
                    log.add("Riga " + numeroRiga + ": id duplicato '" + id + "'");
                    continue;
                }

                coda.add(new Ticket(id, descrizione, livello, timestamp));
            }
        }
        return coda;
    }

    static void scrivi(BufferedWriter writer, String riga) throws IOException {
        System.out.println(riga);
        writer.write(riga);
        writer.newLine();
    }

    static void elaboraEScriviReport(PriorityQueue<Ticket> coda, Path output) throws IOException {
        EnumMap<Livello, Integer> conteggio = new EnumMap<>(Livello.class);
        int totale = 0;
        int consecutiviUrgenti = 0;
        int numeroPause = 0;
        long tempoCumulativo = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            scrivi(writer, "REPORT DI LAVORAZIONE TICKET");
            scrivi(writer, "=".repeat(80));

            while (!coda.isEmpty()) {
                Ticket t = coda.poll();

                if (t.livello.urgente() && consecutiviUrgenti == MAX_URGENTI_CONSECUTIVI) {
                    tempoCumulativo += PAUSA_MINUTI;
                    numeroPause++;
                    consecutiviUrgenti = 0;
                    scrivi(writer, String.format("     --- PAUSA OBBLIGATORIA di %d min (t = %d min) ---",
                            PAUSA_MINUTI, tempoCumulativo));
                }

                tempoCumulativo += t.livello.minutiLavorazione;
                totale++;
                conteggio.merge(t.livello, 1, Integer::sum);

                if (t.livello.urgente()) {
                    consecutiviUrgenti++;
                } else {
                    consecutiviUrgenti = 0;
                }

                scrivi(writer, String.format("%2d.  %-5s %-8s %-40s completato a t = %d min",
                        totale, t.id, t.livello, t.descrizione, tempoCumulativo));
            }

            scrivi(writer, "=".repeat(80));
            scrivi(writer, "RIEPILOGO");
            scrivi(writer, "Totale ticket lavorati: " + totale);
            for (Livello livello : Livello.values()) {
                scrivi(writer, "  " + livello + ": " + conteggio.getOrDefault(livello, 0));
            }
            scrivi(writer, "Pause obbligatorie: " + numeroPause);
            scrivi(writer, "Tempo totale stimato (pause incluse): " + tempoCumulativo + " min");
        }
    }

    static void scriviLog(List<String> log, Path fileLog) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(fileLog, StandardCharsets.UTF_8)) {
            if (log.isEmpty()) {
                writer.write("Nessuna riga malformata.");
                writer.newLine();
            }
            for (String messaggio : log) {
                writer.write(messaggio);
                writer.newLine();
            }
        }
    }

    public static void main(String[] args) {
        Path input = Paths.get("ticket.csv");
        Path output = Paths.get("report_lavorazione.txt");
        Path fileLog = Paths.get("log_errori.txt");
        List<String> log = new ArrayList<>();

        PriorityQueue<Ticket> coda;
        try {
            coda = leggiTicket(input, log);
        } catch (NoSuchFileException e) {
            System.err.println("File non trovato: " + e.getFile()
                    + ". Controlla il nome e che si trovi nella cartella di esecuzione.");
            return;
        } catch (IOException e) {
            System.err.println("Errore di lettura di " + input + ": " + e.getMessage());
            return;
        }

        try {
            scriviLog(log, fileLog);
            System.out.println("Righe malformate segnalate: " + log.size() + " (vedi " + fileLog + ")");
            System.out.println();
            elaboraEScriviReport(coda, output);
        } catch (IOException e) {
            System.err.println("Errore di scrittura: " + e.getMessage());
        }
    }
}