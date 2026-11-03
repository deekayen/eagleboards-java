package shkc.core;

/**
 * One column of one record that an action changed, remembered so the Event
 * page's Undo can put it back.
 *
 * @param recordType which file the record is in: {@code "Scout"}, {@code "Room"} or {@code "Adult"}
 * @param id         the record's ID within that file
 * @param column     the column that was changed
 * @param oldValue   the value before the action, which Undo restores
 * @param newValue   the value the action wrote, which Undo checks is still there
 */
public record UndoField(String recordType, String id, String column, String oldValue, String newValue) {
}
