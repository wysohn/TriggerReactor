package io.github.wysohn.triggerreactor.core.manager.trigger.command;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The tab completer interface, which is responsible for handling tab completion process. Each tab completer must handle
 * tab completion request per argument index. For example, 0th argument has its own ITabCompleter, 1st argument has
 * another ITabCompleter, and so on.
 */
public interface ITabCompleter {

    /**
     * Get possible candidates commands, which can fit into the current argument the player is about to enter.
     * This only works when player enters command at least 1 character, and naturally, the method is expected to return
     * only the possible candidates that starts with the given character(s) to minimize the overhead.
     * <br><br>
     * For example, if a player enter `/mycmd abc def g' in the command line, the candidate commands at argument index 2 that
     * are starting with `g` will show up to the user as tab completion selections.
     *
     * @param part the partially completed command.
     * @return list of candidates which can fit
     */
    List<String> getCandidates(String part);

    /**
     * Get simple help string that will show up to the player. Unlike {@link #getCandidates(String)}, this method
     * will be called when there is no character exist in the place of current argument.
     * <br><br>
     * For example, you may decide to show simple String, [player], so the player can effectively know what to enter
     * at the current argument index. If this method returns the same value as {@link #getCandidates(String)},
     * it will behave as same as the naive tab completer.
     *
     * @return list of String to be shown as hint.
     */
    List<String> getHint();




    class TabCompleterBuilder{

        List<String> hint,candidate;

        private TabCompleterBuilder(){
            this.hint = new ArrayList<>();
            this.candidate = new ArrayList<>();
        }

        private TabCompleterBuilder(String... arg){
            this.hint = Arrays.stream(arg)
                    .collect(Collectors.toList());
            this.candidate = this.hint;
        }

        private TabCompleterBuilder(Template template){
            if(template == Template.EMPTY){
                this.hint = new ArrayList<>();
                this.candidate = new ArrayList<>();
            }
            if(template == Template.PLAYER){
                this.hint = list("<player>");
                this.candidate = null; // returning null signals to list online players instead
                //TODO not sure if Sponge does the same
            }
        }

        public static TabCompleterBuilder self(){
            return new TabCompleterBuilder();
        }

        public static TabCompleterBuilder of(String... arg){
            return new TabCompleterBuilder(arg);
        }

        public static TabCompleterBuilder of(Template template){
            return new TabCompleterBuilder(template);
        }

        public static TabCompleterBuilder withHint(String hint){
            return TabCompleterBuilder.self().appendHint(hint);
        }

        public TabCompleterBuilder appendHint(String... hints){
            this.hint.addAll(list(hints));

            return this;
        }
        public TabCompleterBuilder appendHint(Collection<String> hints){
            this.hint.addAll(hints);

            return this;
        }
        public TabCompleterBuilder appendCandidate(String... candidates){
            this.candidate.addAll(list(candidates));

            return this;
        }
        public TabCompleterBuilder appendCandidate(Collection<String> candidates){
            this.candidate.addAll(candidates);

            return this;
        }
        public TabCompleterBuilder setHint(String...hints){
            this.hint = list(hints);

            return this;
        }
        public TabCompleterBuilder setHint(Collection<String> hints){
            this.hint = hints instanceof List ? (List<String>) hints : new ArrayList<>(hints);

            return this;
        }
        public TabCompleterBuilder setCandidate(String...candidates){
            this.candidate = list(candidates);

            return this;
        }
        public TabCompleterBuilder setCandidate(Collection<String> candidates){
            if(candidates == null)
                this.candidate = null;
            else
                this.candidate = candidates instanceof List ? (List<String>) candidates : new ArrayList<>(candidates);

            return this;
        }
        public TabCompleterBuilder setAsPlayerList(){
            this.hint = list("<player>");
            this.candidate = null;

            return this;
        }



        public ITabCompleter build(){
            return new ITabCompleter() {
                @Override
                public List<String> getCandidates(String part) {
                    return candidate.stream()
                            .filter(val -> val.startsWith(part))
                            .collect(Collectors.toList());
                }
                @Override
                public List<String> getHint() {
                    return hint;
                }
            };

        }

    }


    static List<String> list(String... strings) {
        return Arrays.stream(strings).collect(Collectors.toList());
    }

    enum Template{
        EMPTY,PLAYER
    }
}