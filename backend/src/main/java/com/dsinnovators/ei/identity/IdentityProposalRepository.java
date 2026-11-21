package com.dsinnovators.ei.identity;

import com.dsinnovators.ei.identity.ProposalAssignment.PersonUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface IdentityProposalRepository extends Repository<IdentityProposal, Long> {

    IdentityProposal save(IdentityProposal proposal);

    @Query("select p from IdentityProposal p where p.personId = :personId and p.state = :state")
    Optional<IdentityProposal> findOf(@Param("personId") long personId, @Param("state") String state);

    @Query("""
            select new com.dsinnovators.ei.identity.ProposalAssignment$PersonUser(p.personId, p.openprojectUserId)
            from IdentityProposal p where p.state = :state
            """)
    List<PersonUser> pairsIn(@Param("state") String state);

    /** Deletes at once, so a new row for the same person can follow in the same transaction. */
    @Modifying
    @Query("delete from IdentityProposal p where p.personId = :personId and p.state = :state")
    void deleteOf(@Param("personId") long personId, @Param("state") String state);
}
