package io.github.turbopro.ism.iam.console;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.iam.auth.IamErrorCode;
import io.github.turbopro.ism.iam.auth.PasswordPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConsoleUserService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final ConsoleUserMapper mapper;
    private final PasswordEncoder passwords;
    public ConsoleUserService(ConsoleUserMapper mapper,PasswordEncoder passwords){this.mapper=mapper;this.passwords=passwords;}

    public List<ConsoleUserModels.View> users(){
        Map<Long,Set<String>> roles=mapper.roles().stream().collect(Collectors.groupingBy(
                ConsoleUserModels.UserRole::userId,Collectors.mapping(ConsoleUserModels.UserRole::roleCode,Collectors.toSet())));
        return mapper.users().stream().map(row->view(row,roles.getOrDefault(row.id(),Set.of()))).toList();
    }

    @Transactional
    public ConsoleUserModels.View create(ConsoleUserModels.Create command){
        if(!PasswordPolicy.isStrong(command.initialPassword())) throw new ApiException(IamErrorCode.PASSWORD_POLICY);
        long id=RANDOM.nextLong(Long.MAX_VALUE-1)+1;
        try {mapper.insertUser(id,command.username(),command.displayName(),passwords.encode(command.initialPassword()));}
        catch(DuplicateKeyException exception){throw new ApiException(ConsoleUserErrorCode.DUPLICATE);}
        if(mapper.assignRole(id,command.roleCode())!=1) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        return users().stream().filter(user->user.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    @Transactional(readOnly=true)
    public ConsoleUserModels.RoleImpactPreview previewRoleChange(long id,String roleCode,int version,long actorId){
        if(version<0||!Set.of("PLATFORM_ADMIN","PLATFORM_SUPPORT").contains(roleCode))
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        ConsoleUserModels.Row target=mapper.findUser(id);
        if(target==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(target.version()!=version) throw new ApiException(CommonErrorCode.CONFLICT);
        if(mapper.activeRole(roleCode)!=1) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        Set<String> currentRoles=new TreeSet<>(mapper.userRoleCodes(id));
        Set<String> before=new TreeSet<>(mapper.userPermissionCodes(id));
        Set<String> after=new TreeSet<>(mapper.rolePermissionCodes(roleCode));
        Set<String> added=new TreeSet<>(after);added.removeAll(before);
        Set<String> removed=new TreeSet<>(before);removed.removeAll(after);
        Set<String> blockers=new TreeSet<>();
        if(id==actorId) blockers.add("SELF_ROLE_CHANGE");
        if(currentRoles.contains("PLATFORM_ADMIN")=="PLATFORM_ADMIN".equals(roleCode)) blockers.add("NO_CHANGE");
        if(currentRoles.contains("PLATFORM_ADMIN")&&!"PLATFORM_ADMIN".equals(roleCode)
                &&"ACTIVE".equals(target.status())&&!target.manualLocked()&&mapper.activeAdmins()<=1)
            blockers.add("LAST_ADMIN");
        return new ConsoleUserModels.RoleImpactPreview(Long.toString(id),target.username(),version,
                currentRoles,roleCode,added,removed,blockers.isEmpty(),blockers);
    }

    @Transactional
    public ConsoleUserModels.View changeStatus(long id,ConsoleUserModels.ChangeStatus command,long actorId){
        if(mapper.lockPlatform()==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        ConsoleUserModels.Row target=mapper.lockUser(id);
        if(target==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(target.version()!=command.version()||target.status().equals(command.status())) throw new ApiException(CommonErrorCode.CONFLICT);
        if("DISABLED".equals(command.status())){
            if(id==actorId) throw new ApiException(ConsoleUserErrorCode.SELF_DISABLE);
            if(!target.manualLocked()&&mapper.isAdmin(id)>0&&mapper.activeAdmins()<=1) throw new ApiException(ConsoleUserErrorCode.LAST_ADMIN);
        }
        if(mapper.changeStatus(id,command.status(),command.version())!=1) throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.revokeTokens(id,LocalDateTime.now(),"PLATFORM_USER_STATUS");
        return users().stream().filter(user->user.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    @Transactional
    public ConsoleUserModels.View changeRole(long id,ConsoleUserModels.ChangeRole command,long actorId){
        if(mapper.lockPlatform()==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        ConsoleUserModels.Row target=mapper.lockUser(id);
        if(target==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(target.version()!=command.version()) throw new ApiException(CommonErrorCode.CONFLICT);
        if(id==actorId) throw new ApiException(ConsoleUserErrorCode.SELF_ROLE_CHANGE);
        boolean admin=mapper.isAdmin(id)>0;
        if(admin=="PLATFORM_ADMIN".equals(command.roleCode())) throw new ApiException(CommonErrorCode.CONFLICT);
        if(admin&&"ACTIVE".equals(target.status())&&!target.manualLocked()&&mapper.activeAdmins()<=1)
            throw new ApiException(ConsoleUserErrorCode.LAST_ADMIN);
        mapper.clearRoles(id);
        if(mapper.assignRole(id,command.roleCode())!=1) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        if(mapper.bumpSessionVersion(id,command.version())!=1) throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.revokeTokens(id,LocalDateTime.now(),"PLATFORM_USER_ROLE");
        return users().stream().filter(user->user.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    @Transactional
    public ConsoleUserModels.View resetPassword(long id,ConsoleUserModels.ResetPassword command,long actorId){
        if(mapper.lockPlatform()==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        ConsoleUserModels.Row target=mapper.lockUser(id);
        if(target==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(target.version()!=command.version()) throw new ApiException(CommonErrorCode.CONFLICT);
        if(id==actorId) throw new ApiException(ConsoleUserErrorCode.SELF_PASSWORD_RESET);
        if(!PasswordPolicy.isStrong(command.temporaryPassword()) ||
                passwords.matches(command.temporaryPassword(),mapper.passwordHash(id)))
            throw new ApiException(IamErrorCode.PASSWORD_POLICY);
        if(mapper.resetPassword(id,passwords.encode(command.temporaryPassword()),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.revokeTokens(id,LocalDateTime.now(),"PLATFORM_USER_PASSWORD_RESET");
        return users().stream().filter(user->user.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    @Transactional
    public ConsoleUserModels.View changeLoginLock(long id,ConsoleUserModels.ChangeLoginLock command,long actorId){
        if(mapper.lockPlatform()==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        ConsoleUserModels.Row target=mapper.lockUser(id);
        if(target==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(target.version()!=command.version()) throw new ApiException(CommonErrorCode.CONFLICT);
        LocalDateTime now=LocalDateTime.now();
        boolean automaticLocked=target.lockedUntil()!=null&&target.lockedUntil().isAfter(now);
        if(command.locked()){
            if(target.manualLocked()) throw new ApiException(CommonErrorCode.CONFLICT);
            if(id==actorId) throw new ApiException(ConsoleUserErrorCode.SELF_LOGIN_LOCK);
            if("ACTIVE".equals(target.status())&&mapper.isAdmin(id)>0&&mapper.activeAdmins()<=1)
                throw new ApiException(ConsoleUserErrorCode.LAST_ADMIN);
            if(mapper.lockLogin(id,command.reason().trim(),now,actorId,command.version())!=1)
                throw new ApiException(CommonErrorCode.CONFLICT);
        } else {
            if(!target.manualLocked()&&!automaticLocked) throw new ApiException(CommonErrorCode.CONFLICT);
            if(mapper.unlockLogin(id,command.version())!=1) throw new ApiException(CommonErrorCode.CONFLICT);
        }
        mapper.revokeTokens(id,now,"PLATFORM_USER_LOGIN_LOCK");
        return users().stream().filter(user->user.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    private ConsoleUserModels.View view(ConsoleUserModels.Row row,Set<String> roles){
        return new ConsoleUserModels.View(Long.toString(row.id()),row.username(),row.displayName(),row.status(),
                row.forcePasswordChange(),row.manualLocked(),row.manualLockReason(),
                row.lockedUntil()!=null&&row.lockedUntil().isAfter(LocalDateTime.now())?row.lockedUntil():null,
                row.version(),Set.copyOf(roles));
    }
}
