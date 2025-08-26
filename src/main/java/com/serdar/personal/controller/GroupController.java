package com.serdar.personal.controller;

import com.serdar.personal.service.GroupInviteService;
import com.serdar.personal.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;
    private final GroupInviteService inviteService;

    @PostMapping("/create")
    public Map<String,Object> create(@RequestParam String name,
                                     @RequestParam(required=false) String description,
                                     Principal me) {
        var g = groupService.createGroup(name, description, me.getName());
        return Map.of("groupId", g.getId(), "name", g.getName());
    }

    @PostMapping("/{groupId}/invite")
    public void invite(@PathVariable Long groupId,
                       @RequestParam Long toUserId,
                       Principal me) throws AccessDeniedException {
        inviteService.invite(groupId, me.getName(), toUserId);
    }

    @PostMapping("/invites/{inviteId}/respond")
    public void respond(@PathVariable Long inviteId,
                        @RequestParam boolean accept,
                        Principal me) throws AccessDeniedException {
        inviteService.respond(inviteId, me.getName(), accept);
    }

    @GetMapping("/mine")
    public List<Map<String,Object>> mine(Principal me) {
        return groupService.myGroups(me.getName());
    }
}