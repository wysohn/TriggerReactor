/*******************************************************************************
 *     Copyright (C) 2017 wysohn
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package io.github.wysohn.triggerreactor.core.config.serialize;

import io.github.wysohn.gsoncopy.JsonDeserializationContext;
import io.github.wysohn.gsoncopy.JsonElement;
import io.github.wysohn.gsoncopy.JsonPrimitive;
import io.github.wysohn.gsoncopy.JsonSerializationContext;

import java.util.UUID;

public class UUIDSerializer extends CustomSerializer<UUID> {
    public UUIDSerializer() {
        super(UUID.class);
    }

    @Override
    public UUID deserialize(JsonElement json, JsonDeserializationContext context) {
        return UUID.fromString(json.getAsString());
    }

    @Override
    public JsonElement serialize(UUID src, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }
}
